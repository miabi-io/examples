# Java example

A plain Java 25 service with no framework and no runtime dependencies: the JDK's built-in
`HttpServer` on virtual threads. The image carries a runtime built with `jlink` that holds only
the three modules the app uses, on [distroless](https://github.com/GoogleContainerTools/distroless),
so it runs as uid `65532` and the [manifest](../gitops/java/app.yaml) drops every capability.
For a framework, a database and migrations, see the [Spring Boot example](../spring-boot).

| Route | |
|---|---|
| `/` | A page naming the version, architecture and the container that served it |
| `/healthz` | `{"status":"ok"}`, the health check |
| `/api/info` | Version, hostname, Java version, `os/arch`, heap size, uptime |

`/api/info` also returns a `miabi` object with what Miabi set in the container, such as
`MIABI_RELEASE`, `MIABI_NODE` and `MIABI_REPLICA`; outside Miabi it is empty.

`PORT` (default `8080`), `APP_NAME` and `APP_VERSION` are read from the environment. Without
`APP_VERSION`, the version comes from `pom.xml`, through the jar's manifest.

## The image

- **A trimmed runtime.** `jlink --add-modules java.base,java.management,jdk.httpserver` builds a
  Java runtime of about 40 MB instead of shipping a full JRE. Add a module there when the code
  starts using one, such as `java.sql`; `jdeps --print-module-deps target/app.jar` lists them.
- **Multi-arch.** Compiling runs on the build machine's own platform, since bytecode runs
  anywhere. `jlink` also runs there, linking against the target platform's jmods (Temurin 25
  ships none, so the build fetches them from Adoptium for the exact JDK version). No stage needs
  emulation, so `platforms: [linux/amd64, linux/arm64]` works on a runner without QEMU. It needs
  runner 0.0.11 or later.
- **Memory.** `-XX:MaxRAMPercentage=75` sizes the heap from the container's limit, so 128Mi gives
  about 96 MiB of heap. `-XX:+ExitOnOutOfMemoryError` lets Miabi replace a container that runs
  out, instead of leaving it half alive.
- **Shutdown.** A shutdown hook calls `server.stop(5)`, which waits up to 5 seconds for in-flight
  requests when Miabi replaces a release.

The root filesystem stays writable: Miabi's `http` health check copies a small probe into the
container, and distroless has no `curl` or `wget` to fall back on.

## Run it

```bash
./mvnw verify                          # tests start the server on a random port
./mvnw -q package && java -jar target/app.jar
```

Or without a local JDK:

```bash
docker build -t java-example . && docker run --rm -p 8080:8080 java-example
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/java/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml), which runs `mvn verify`
  before the multi-arch build.
