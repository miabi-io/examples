# Python example

A [FastAPI](https://fastapi.tiangolo.com) service on Python 3.13 under uvicorn. With
`DB_URL` set it stores notes in Postgres; without it, everything except the notes API
still works.

| Route | |
|---|---|
| `/` | A page naming the version and whether a database is connected |
| `/healthz` | `{"status":"ok"}`, the health check |
| `/api/info` | Version, hostname, Python version, database status |
| `GET /api/notes` | The latest 50 notes |
| `POST /api/notes` | `{"body": "…"}` creates a note |
| `/docs` | OpenAPI docs |

`/api/info` also returns a `miabi` object with what Miabi set in the container, such as
`MIABI_RELEASE`, `MIABI_NODE` and `MIABI_REPLICA`; outside Miabi it is empty.

The `notes` table is created on startup if it does not exist.

## The database

The [manifest](../gitops/python/app.yaml) declares a managed Postgres database next to the app
and passes its connection string in, so no credential appears in the file:

```yaml
env:
  DB_URL: "{{ .databases.python-example-db.uri }}"
secretEnv:
  - DB_URL              # stored encrypted at rest
```

The database is created before the app on the first apply.

## Run it

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/pytest
DB_URL=postgres://postgres:postgres@localhost:5432/postgres .venv/bin/uvicorn app.main:app --reload
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/python/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml), which runs `pytest`
  before the build.
