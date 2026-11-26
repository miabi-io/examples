# Node.js example

An [Express](https://expressjs.com) app on Node.js 22. It runs as the unprivileged `node` user
and closes in-flight requests on `SIGTERM`, so a rolling deploy drops none.

| Route | |
|---|---|
| `/` | A page naming the version and the container that served it |
| `/healthz` | `{"status":"ok"}`, the health check |
| `/api/info` | Version, hostname, Node version, uptime |
| `/api/upstream` | Calls `UPSTREAM_URL/api/info`. Used in [`gitops/full-stack`](../gitops/full-stack) |

`/api/info` also returns a `miabi` object with what Miabi set in the container, such as
`MIABI_RELEASE`, `MIABI_NODE` and `MIABI_REPLICA`; outside Miabi it is empty.

| Variable | Default | |
|---|---|---|
| `PORT` | `3000` | Port to listen on |
| `APP_NAME` | `node-example` | Shown on the page |
| `APP_VERSION` | `dev` | Shown on the page |
| `UPSTREAM_URL` | — | Another app to call, such as `{{ .applications.notes-api }}` |

## Run it

```bash
npm install && npm test && npm start
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/node/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml), which runs `npm test`,
  builds the Dockerfile on your runner and deploys the result.
