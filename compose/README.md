# Docker Compose example

A front end, an API and Postgres in one [`docker-compose.yml`](docker-compose.yml): the
[Node](../node) app calls the [Python](../python) API by its service name, and the API stores
notes in Postgres. The same file runs locally with `docker compose` and imports into Miabi as a
[stack](https://docs.miabi.io/docs/applications/stacks#importing-a-docker-compose-file).

## Run it locally

```bash
cp .env.example .env          # set POSTGRES_PASSWORD
docker compose up -d
docker compose port web 3000  # the host port Docker picked for the front end
```

## Import it into Miabi

With the [CLI](https://docs.miabi.io/docs/cicd/cli), from this folder:

```bash
miabi stacks import --deploy --wait
```

The CLI reads `.env` beside the file, so `${POSTGRES_PASSWORD}` is filled in before the file is
sent. Or open **Stacks → Import Compose** in the console and paste the file.

Miabi creates a stack named `board` (the file's `name:`) with one app per service, `web`, `api`
and `db`, and a volume `board-db-data` for the Postgres data. The services reach each other by
name on the stack's network, as they did under Docker Compose.

What the import leaves for you:

- **Secrets.** `DB_URL` and `POSTGRES_PASSWORD` arrive as plain variables. Mark them as secrets
  on each app.
- **A public URL.** `ports: ["3000"]` declares the container port only. Add a route, or turn on
  external access for `web`.
- **Startup order.** `depends_on` and `restart` are ignored. The API connects to Postgres when it
  starts and exits if it can't, so its first start may fail until `db` is ready; Miabi restarts it.

## Moving to manifests

[`gitops/stack`](../gitops/stack) is this file written as `miabi.io/v1` manifests: the same two
apps in a `Stack`, with a managed Postgres in place of the `db` container, so backups, upgrades
and credentials are handled by Miabi.
