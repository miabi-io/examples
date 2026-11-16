package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/jkaninda/okapi/okapitest"
)

func newTestServer(t *testing.T) string {
	t.Helper()
	srv := httptest.NewServer(newApp(time.Now()))
	t.Cleanup(srv.Close)
	return srv.URL
}

func TestHealthz(t *testing.T) {
	okapitest.GET(t, newTestServer(t)+"/healthz").
		ExpectStatusOK().
		ExpectJSONPath("status", "ok")
}

func TestInfoReportsVersion(t *testing.T) {
	version = "1.2.3"
	t.Cleanup(func() { version = "dev" })

	okapitest.GET(t, newTestServer(t)+"/api/info").
		ExpectStatusOK().
		ExpectJSONPath("version", "1.2.3").
		ExpectJSONPath("app", "go-example")
}

func TestIndexRenders(t *testing.T) {
	okapitest.GET(t, newTestServer(t)+"/").
		ExpectStatusOK().
		ExpectBodyContains("Hello from Go on Miabi")
}

func TestUnknownPathIsNotFound(t *testing.T) {
	okapitest.GET(t, newTestServer(t)+"/missing").ExpectStatusNotFound()
}

func TestNotesAreOffWithoutDataDir(t *testing.T) {
	t.Setenv("DATA_DIR", "")
	okapitest.GET(t, newTestServer(t)+"/api/notes").ExpectStatus(http.StatusServiceUnavailable)
}

func TestNotesPersistInDataDir(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("DATA_DIR", dir)

	okapitest.POST(t, newTestServer(t)+"/api/notes").
		JSONBody(map[string]string{"text": "hello <volume>"}).
		ExpectStatusCreated().
		ExpectJSONPath("text", "hello <volume>")

	// A fresh server reads what the first one wrote, as a new release would.
	srv := newTestServer(t)
	var got []Note
	okapitest.GET(t, srv+"/api/notes").ExpectStatusOK().ParseJSON(&got)
	if len(got) != 1 || got[0].Text != "hello <volume>" {
		t.Fatalf("notes = %+v", got)
	}
	okapitest.GET(t, srv+"/").ExpectStatusOK().
		ExpectBodyContains("hello &lt;volume&gt;").
		ExpectBodyNotContains("hello <volume>")
}

func TestEmptyNoteIsRejected(t *testing.T) {
	t.Setenv("DATA_DIR", t.TempDir())
	okapitest.POST(t, newTestServer(t)+"/api/notes").
		JSONBody(map[string]string{"text": ""}).
		ExpectStatusBadRequest()
}

func TestBannerComesFromTheMountedFile(t *testing.T) {
	banner := filepath.Join(t.TempDir(), "banner.txt")
	if err := os.WriteFile(banner, []byte("Served from a Miabi config\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	t.Setenv("BANNER_FILE", banner)
	okapitest.GET(t, newTestServer(t)+"/").ExpectStatusOK().ExpectBodyContains("Served from a Miabi config")
}

func TestProbe(t *testing.T) {
	srv := httptest.NewServer(newApp(time.Now()))
	t.Cleanup(srv.Close)
	port, _ := strconv.Atoi(srv.URL[strings.LastIndex(srv.URL, ":")+1:])
	if got := probe(port); got != 0 {
		t.Fatalf("probe(running) = %d, want 0", got)
	}
	srv.Close()
	if got := probe(port); got != 1 {
		t.Fatalf("probe(stopped) = %d, want 1", got)
	}
}
