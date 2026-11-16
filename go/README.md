# Go example

An API built with [Okapi](https://github.com/jkaninda/okapi), compiled to a static binary on
[distroless](https://github.com/GoogleContainerTools/distroless). The image has no shell and
runs as uid `65532`, so the [manifest](../gitops/go/app.yaml) drops every capability and
forbids new privileges.

The root filesystem stays writable. Miabi's `http` health check copies a small probe into the
container. With `readOnlyRootFilesystem: true` it can't, and it falls back to `curl` or `wget`
from the image, which distroless doesn't have, so every release would report unhealthy.

| Route | |
|---|---|
| `/` | A page naming the version, architecture and the container that served it |
| `/healthz` | `{"status":"ok"}`, the health check |
| `/api/info` | Version, hostname, Go version, `os/arch`, uptime |
| `/api/notes` | `GET` lists the notes, `POST {"text": "…"}` adds one |
| `/docs` | OpenAPI docs Okapi generates from the routes |

| Variable | Default | |
|---|---|---|
| `PORT` | `8080` | Port to listen on |
| `APP_NAME` | `go-example` | Shown on the page |
| `DATA_DIR` | — | Directory the notes are written to, one JSON line per note. Unset, notes are off |
| `BANNER_FILE` | — | A text file shown at the top of the page, such as a mounted config |

## Notes on a volume

Mount a volume at `DATA_DIR` and the notes survive every redeploy. Several replicas can share it
on a [replicated storage class](../gitops/replicated-storage): each note is one appended line,
tagged with the container that wrote it, so the page shows which replica wrote what. On a volume
mounted read-only, adding a note fails with *the volume at DATA_DIR is mounted read-only*. See
[`gitops/volumes`](../gitops/volumes).

The image creates `/data` owned by the nonroot user, and Docker copies that ownership into an
empty volume mounted there.

## Health check

The image declares `HEALTHCHECK CMD ["/server", "healthcheck"]`: the binary probes its own
`/healthz`, because distroless has no `curl` or shell. A `container` app can use Miabi's `http`
check. A `service` app in a Swarm cluster probes `http` with the image's `curl` or `wget`, so it
uses `healthcheck: { type: image }` instead.

## Versioning

The binary reports `VERSION` plus the short commit, such as `1.0.0-3f9c2a1`. A pipeline's
`build` step sees no environment, so the [pipeline](.miabi/pipeline.yaml) writes the version to
`.version` in an earlier step, and the [Dockerfile](Dockerfile) reads it. A local build without
it falls back to `VERSION`.

## Multi-arch

The Dockerfile compiles on the build machine's own platform and cross-compiles to the target,
so `platforms: [linux/amd64, linux/arm64]` builds both without emulation. It needs runner
0.0.11 or later.

## Run it

```bash
go test ./... && go run .
```

The tests use Okapi's `okapitest` client against an `httptest` server.

```bash
curl localhost:8080/openapi.json
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/go/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml): test, lint, version,
  multi-arch build, deploy.
