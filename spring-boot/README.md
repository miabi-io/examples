# Spring Boot example

A [Spring Boot](https://spring.io/projects/spring-boot) 4 service on Java 25, with a notes API
stored in Postgres. With `DB_URL` set it connects to Postgres; without it, Boot starts an
embedded H2 database, so the app runs locally with nothing else installed.

| Route | |
|---|---|
| `/` | A page naming the version, Java version and the container that served it |
| `/healthz` | `{"status":"UP"}`, the health check. Includes the database |
| `/healthz/liveness`, `/healthz/readiness` | Spring Boot's liveness and readiness probes |
| `/api/info` | Version, hostname, Java version, database, heap size, uptime |
| `GET /api/notes` | The latest 50 notes |
| `POST /api/notes` | `{"body": "…"}` creates a note |

`/api/info` also returns a `miabi` object with what Miabi set in the container, such as
`MIABI_RELEASE`, `MIABI_NODE` and `MIABI_REPLICA`; outside Miabi it is empty.

The `notes` table is created by a [Flyway](https://www.red-gate.com/products/flyway/community/)
migration in [`db/migration`](src/main/resources/db/migration) on startup.

## The database

The [manifest](../gitops/spring-boot/app.yaml) declares a managed Postgres database next to the
app and passes its connection string in, so no credential appears in the file:

```yaml
env:
  DB_URL: "{{ .databases.spring-boot-example-db.uri }}"
secretEnv:
  - DB_URL              # stored encrypted at rest
```

Miabi hands out a `postgres://user:password@host:port/db` URI, which JDBC doesn't accept.
[`DatabaseUrl`](src/main/java/io/miabi/examples/DatabaseUrl.java) splits it into
`spring.datasource.url`, `username` and `password` before the application starts. They are set as
default properties, so `SPRING_DATASOURCE_*` variables still win if you prefer to set those.

## Running on Miabi

- **Memory.** The JVM sizes its heap from the container's limit: `-XX:MaxRAMPercentage=75` in the
  [Dockerfile](Dockerfile) gives a 512Mi app about 370 MiB of heap and leaves the rest for
  metaspace and threads. `-XX:+ExitOnOutOfMemoryError` lets Miabi replace a container that runs
  out, instead of leaving it half alive.
- **Startup.** The JVM takes a few seconds to start, so the health check has
  `startPeriodSeconds: 30`. Failures in that window don't count.
- **Shutdown.** `server.shutdown: graceful` finishes in-flight requests when Miabi replaces a
  release, within 8 seconds, before Docker's 10-second kill.
- **Behind the gateway.** TLS ends at the Miabi gateway, so `server.forward-headers-strategy:
  native` trusts its `X-Forwarded-*` headers for redirects and the request scheme.
- **Layers.** The image is built from the jar's layers: dependencies first, the application
  last, so a release that only changes code pushes and pulls one small layer.
- **Multi-arch.** Java bytecode runs anywhere, so the build stage runs on the build machine's own
  platform and `platforms: [linux/amd64, linux/arm64]` builds both without emulation. It needs
  runner 0.0.11 or later.

`PORT` (default `8080`), `APP_NAME` and `APP_VERSION` are read from the environment. Without
`APP_VERSION`, the version comes from `pom.xml`.

## Run it

```bash
./mvnw verify                 # tests run against H2
./mvnw spring-boot:run
DB_URL=postgres://postgres:postgres@localhost:5432/postgres ./mvnw spring-boot:run
```

Or without a local JDK:

```bash
docker build -t spring-boot-example . && docker run --rm -p 8080:8080 spring-boot-example
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/spring-boot/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml), which runs `mvn verify`
  before the multi-arch build.
