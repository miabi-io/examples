# Miabi examples

Small, real applications you can deploy on [Miabi](https://miabi.io), with the pipelines and
GitOps manifests that ship them. Each one builds, passes its own tests, answers a health check,
and shuts down cleanly when Miabi replaces a release.

| Example | Stack | Port | Shows |
|---|---|---|---|
| [`node/`](node) | Node.js 22, Express | 3000 | Tests with `node:test`, calling another app by name |
| [`go/`](go) | Go, [Okapi](https://github.com/jkaninda/okapi), distroless | 8080 | Multi-arch builds, version stamping, no capabilities, notes on a volume |
| [`python/`](python) | Python 3.13, FastAPI | 8000 | A managed Postgres database wired in through `DB_URL` |
| [`php/`](php) | PHP 8.4 on [`jkaninda/nginx-php-fpm`](https://github.com/jkaninda/nginx-php-fpm) | 80 | Plain PHP, plus a managed Redis visit counter |
| [`laravel/`](laravel) | Laravel 13 on `jkaninda/nginx-php-fpm` | 80 | Postgres, Redis, queue worker, migrations as a Job, a CronJob |
| [`java/`](java) | Java 25, JDK `HttpServer`, distroless | 8080 | No framework, a `jlink` runtime of about 40 MB, no capabilities |
| [`spring-boot/`](spring-boot) | Java 25, Spring Boot 4 | 8080 | Postgres with Flyway, JVM sizing from the memory limit, graceful shutdown |
| [`compose/`](compose) | Docker Compose | | A front end, an API and Postgres, imported into Miabi as a stack |
| [`gitops/`](gitops) | `miabi.io/v1` manifests | | One folder per Git source: each app, a stack, volumes and configs, replicated storage, a multi-app project, staging and production |
| [`pipelines/`](pipelines) | `kind: Pipeline` specs | | Monorepo builds, build-only images, release with scan and notify, GitHub Actions |

## Four ways to deploy an example

**1. Apply a manifest.** The images are published to `ghcr.io/miabi-io/examples/<name>`, so a
manifest from [`gitops/`](gitops) deploys as-is:

```bash
miabi login
miabi apply -f gitops/python/app.yaml --dry-run   # print the plan
miabi apply -f gitops/python/app.yaml             # Postgres + the app, wired together
```

**2. Reconcile from Git.** Point a [Git source](https://docs.miabi.io/docs/cicd/gitops) at this
repository and one folder, such as `gitops/laravel`. Miabi keeps the workspace matching that
folder: a commit is a change, a revert is a rollback. See [`gitops/`](gitops).

**3. Import a Docker Compose file.** Already have a `docker-compose.yml`? `miabi stacks import`
turns each service into an app in a stack. Try it on [`compose/`](compose).

**4. Build from source with a pipeline.** Copy an example folder into its own repository and
create an app from it in Miabi. Miabi finds `.miabi/pipeline.yaml` and offers to adopt it, and
from then on every deploy runs the tests, builds the image on your runner, and rolls it out. To
build straight from this monorepo instead, use the specs in
[`pipelines/monorepo`](pipelines/monorepo).

## Layout

```
.
├── node/  go/  python/  php/  laravel/  java/  spring-boot/   # the apps: source, tests, Dockerfile, .miabi/pipeline.yaml
├── compose/                                                  # a docker-compose.yml to import as a stack
├── gitops/                                                   # miabi.io/v1 manifests, one folder per Git source
├── pipelines/                                                # pipeline specs for this monorepo, GitHub Actions
└── .github/workflows/                                        # CI for every example; publishes the ghcr.io images
```

Each app answers on the same routes, so you can check any of them the same way:

| Route | Returns |
|---|---|
| `/` | A page naming the version, runtime and the container that served it |
| `/healthz` (`/up` for Laravel) | `200` when the app is ready. The manifests use it as the health check |
| `/api/info` | The same details as JSON |

## Prerequisites

- A Miabi install with an external domain, for the `externalAccess` URLs. A custom hostname
  needs a [`Domain` and `Route`](gitops/full-stack) instead.
- A [build runner](https://docs.miabi.io/docs/cicd/runners) registered in the workspace for
  anything that builds from source. Applying a manifest only pulls images, so it needs none.
- The [`miabi` CLI](https://docs.miabi.io/docs/cicd/cli), or the console, which does the same.

## Run an example locally

```bash
docker build -t example ./go && docker run --rm -p 8080:8080 example
```

The PHP and Laravel images listen on port 80, Node on 3000, Python on 8000, and Go, Java and
Spring Boot on 8080.

## Publishing the images

[`images.yml`](.github/workflows/images.yml) builds every example for `linux/amd64` and
`linux/arm64` and pushes it to `ghcr.io/miabi-io/examples/<name>`: `latest` and `sha-<short>`
from `main`, plus the version tags from a `v*` tag, such as `1.0.0` for `v1.0.0`. The
[GitOps examples](gitops) pin `1.1.0`, so tag a release before you use them. GHCR makes a new package private, so set each package to public once after its first push.

## Documentation

- [GitOps](https://docs.miabi.io/docs/cicd/gitops) and the
  [manifest reference](https://docs.miabi.io/docs/cicd/manifest-reference)
- [Pipelines](https://docs.miabi.io/docs/cicd/pipelines) and
  [build runners](https://docs.miabi.io/docs/cicd/runners)
- [GitHub Actions](https://docs.miabi.io/docs/cicd/github-actions)
- [Stacks](https://docs.miabi.io/docs/applications/stacks), [volumes](https://docs.miabi.io/docs/storage/volumes)
  and [storage classes](https://docs.miabi.io/docs/storage/storage-classes)
