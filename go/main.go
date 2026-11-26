// Command server is a small Okapi service deployed on Miabi.
package main

import (
	"context"
	"errors"
	"html/template"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/jkaninda/okapi"
)

// version is stamped at build time with -ldflags "-X main.version=...".
var version = "dev"

type Health struct {
	Status string `json:"status" example:"ok"`
}

type Info struct {
	App           string            `json:"app"`
	Version       string            `json:"version"`
	Hostname      string            `json:"hostname"`
	Go            string            `json:"go"`
	Arch          string            `json:"arch"`
	UptimeSeconds int64             `json:"uptimeSeconds"`
	Miabi         map[string]string `json:"miabi"`
}

// An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
var miabiVars = []string{
	"MIABI_APP_NAME", "MIABI_WORKSPACE_NAME", "MIABI_STACK", "MIABI_RELEASE", "MIABI_VERSION",
	"MIABI_COMMIT", "MIABI_LOCATION", "MIABI_NODE", "MIABI_REPLICA", "MIABI_PUBLIC_URL",
}

func (i Info) ReleaseLine() string {
	if i.Miabi["MIABI_RELEASE"] == "" {
		return ""
	}
	line := "Release " + i.Miabi["MIABI_RELEASE"]
	if node := i.Miabi["MIABI_NODE"]; node != "" {
		line += " on node " + node
	}
	if replica := i.Miabi["MIABI_REPLICA"]; replica != "" {
		line += ", replica " + replica
	}
	return line
}

func miabiEnv() map[string]string {
	m := map[string]string{}
	for _, k := range miabiVars {
		if v := os.Getenv(k); v != "" {
			m[k] = v
		}
	}
	return m
}

func main() {
	port, err := strconv.Atoi(envOr("PORT", "8080"))
	if err != nil {
		slog.Error("invalid PORT", "err", err)
		os.Exit(1)
	}
	// Distroless has no curl or shell, so the image's HEALTHCHECK runs the binary itself.
	if len(os.Args) > 1 && os.Args[1] == "healthcheck" {
		os.Exit(probe(port))
	}
	app := newApp(time.Now()).WithPort(port)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		slog.Info("listening", "port", port, "version", version)
		if err := app.Start(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("server failed", "err", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	slog.Info("shutting down")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := app.StopWithContext(shutdownCtx); err != nil {
		slog.Error("shutdown", "err", err)
	}
}

func newApp(started time.Time) *okapi.Okapi {
	hostname, _ := os.Hostname()
	current := func() Info {
		return Info{
			App:           envOr("APP_NAME", envOr("MIABI_APP_NAME", "go-example")),
			Version:       version,
			Hostname:      hostname,
			Go:            runtime.Version(),
			Arch:          runtime.GOOS + "/" + runtime.GOARCH,
			UptimeSeconds: int64(time.Since(started).Seconds()),
			Miabi:         miabiEnv(),
		}
	}

	app := okapi.New().WithOpenAPIDocs(okapi.OpenAPI{Title: "go-example", Version: version})

	app.Get("/healthz", func(c *okapi.Context) error {
		return c.OK(Health{Status: "ok"})
	}, okapi.DocSummary("Health check"), okapi.DocResponse(&Health{}))

	app.Get("/api/info", func(c *okapi.Context) error {
		return c.OK(current())
	}, okapi.DocSummary("Build and runtime details"), okapi.DocResponse(&Info{}))

	notes := &noteStore{dir: os.Getenv("DATA_DIR")}
	bannerFile := os.Getenv("BANNER_FILE")

	app.Get("/api/notes", func(c *okapi.Context) error {
		list, err := notes.List()
		if err != nil {
			return c.ErrorServiceUnavailable(err.Error())
		}
		return c.OK(list)
	}, okapi.DocSummary("Notes stored on the volume at DATA_DIR"), okapi.DocResponse([]Note{}))

	app.Post("/api/notes", func(c *okapi.Context) error {
		var in NoteInput
		if err := c.Bind(&in); err != nil {
			return c.ErrorBadRequest(err.Error())
		}
		n, err := notes.Add(in.Text, hostname)
		if err != nil {
			return c.ErrorServiceUnavailable(err.Error())
		}
		return c.Created(n)
	}, okapi.DocSummary("Add a note"), okapi.DocRequestBody(&NoteInput{}), okapi.DocResponse(&Note{}))

	app.Post("/notes", func(c *okapi.Context) error {
		if text := strings.TrimSpace(c.FormValue("text")); text != "" && len(text) <= 280 {
			if _, err := notes.Add(text, hostname); err != nil {
				return c.ErrorServiceUnavailable(err.Error())
			}
		}
		c.Redirect(http.StatusSeeOther, "/")
		return nil
	}, okapi.DocHide())

	app.Get("/", func(c *okapi.Context) error {
		list, err := notes.List()
		view := pageData{Info: current(), Banner: readBanner(bannerFile), Notes: list}
		if err != nil {
			view.NotesError = err.Error()
		}
		var b strings.Builder
		if err := page.Execute(&b, view); err != nil {
			return err
		}
		return c.HTMLView(http.StatusOK, b.String(), nil)
	}, okapi.DocHide())

	return app
}

func probe(port int) int {
	client := http.Client{Timeout: 3 * time.Second}
	resp, err := client.Get("http://127.0.0.1:" + strconv.Itoa(port) + "/healthz")
	if err != nil {
		return 1
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

type pageData struct {
	Info       Info
	Banner     string
	Notes      []Note
	NotesError string
}

var page = template.Must(template.New("page").Parse(`<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{{.Info.App}}</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}code{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}.banner{background:#f4effe;border-left:4px solid #8b5cf6;padding:.6rem 1rem}li small{color:#666}input{font:inherit;padding:.3rem;width:70%}</style></head>
<body>
<h1>Hello from Go on Miabi</h1>
{{with .Banner}}<p class="banner">{{.}}</p>{{end}}
<p>Version <code>{{.Info.Version}}</code> · {{.Info.Go}} · <code>{{.Info.Arch}}</code> · served by <code>{{.Info.Hostname}}</code></p>
{{with .Info.ReleaseLine}}<p>{{.}}</p>{{end}}
<h2>Notes</h2>
{{if .NotesError}}<p>{{.NotesError}}</p>{{else}}
<form method="post" action="/notes"><input name="text" maxlength="280" placeholder="Write a note" required> <button>Add</button></form>
<ul>{{range .Notes}}<li>{{.Text}} <small>from <code>{{.Host}}</code> at {{.At.Format "2006-01-02 15:04:05"}}</small></li>{{else}}<li>No notes yet.</li>{{end}}</ul>
{{end}}
<p>Built with <a href="https://github.com/jkaninda/okapi">Okapi</a>. Try <a href="/api/info">/api/info</a>, <a href="/api/notes">/api/notes</a>, <a href="/healthz">/healthz</a> or the API docs at <a href="/docs">/docs</a>.</p>
</body>
</html>`))
