# Pipeline examples

A [pipeline](https://docs.miabi.io/docs/cicd/pipelines) turns one commit into an image and, when
it is bound to an application, deploys exactly that image. The steps run on your
[build runners](https://docs.miabi.io/docs/cicd/runners), never on the nodes that host your apps.

Each example app also carries its own `.miabi/pipeline.yaml`, for when the folder is the root of
its own repository. The specs here are for building straight from this monorepo, and for
patterns that need no app at all.

| File | Bind to | Does |
|---|---|---|
| [`monorepo/node.yaml`](monorepo/node.yaml) | An app | `npm test`, build `node/`, deploy |
| [`monorepo/go.yaml`](monorepo/go.yaml) | An app | `go test`, stamp the version, build `go/` for amd64 and arm64, deploy |
| [`monorepo/python.yaml`](monorepo/python.yaml) | An app | `pytest`, build `python/`, deploy |
| [`monorepo/php.yaml`](monorepo/php.yaml) | An app | Lint and test, build `php/`, deploy |
| [`monorepo/laravel.yaml`](monorepo/laravel.yaml) | An app | `php artisan test`, build `laravel/`, Trivy scan, deploy |
| [`monorepo/java.yaml`](monorepo/java.yaml) | An app | `mvn verify`, build `java/` for amd64 and arm64, deploy |
| [`monorepo/spring-boot.yaml`](monorepo/spring-boot.yaml) | An app | `mvn verify`, build `spring-boot/` for amd64 and arm64, deploy |
| [`build-only.yaml`](build-only.yaml) | A Git repository | Test and build `go/`, push the image, deploy nothing. Also weekly |
| [`release.yaml`](release.yaml) | An app | Test, build a versioned `v1.x.y` image, scan, Slack notice, deploy. Also nightly |
| [`github-actions/deploy.yml`](github-actions/deploy.yml) | — | Build on GitHub, push to GHCR, deploy with `miabi-io/deploy-action` |
| [`github-actions/trigger-pipeline.yml`](github-actions/trigger-pipeline.yml) | — | Gate on GitHub, then start a Miabi pipeline at the same commit |

## Build one example from this monorepo

An app built from a Git repository builds the `Dockerfile` at its root, and this repository has
none. A pipeline can point anywhere: `context` picks the directory and `dockerfile` the file,
both relative to the repository root.

```yaml
  - name: build
    uses: build
    context: node
    dockerfile: node/Dockerfile
```

Container steps start in the repository root too, so their commands `cd` into the example first.

### 1. Create the app

In the console, create an application from Git with `https://github.com/miabi-io/examples`,
branch `main`, and the example's port (3000 for Node, 8080 for Go, Java and Spring Boot, 8000 for Python,
80 for PHP and Laravel). Decline the offer to use a pipeline from the repository: there is none at the root.

### 2. Create the pipeline

**Pipelines → New pipeline**, choose the application as the source, and paste the spec. Or over
the API, with `$APP` the application's id:

```bash
jq -Rs --argjson app "$APP" '{name: "node-example", application_id: $app, spec: ., enabled: true}' \
  < pipelines/monorepo/node.yaml |
curl -X POST "$MIABI_SERVER/api/v1/workspaces/$WORKSPACE/pipelines" \
  -H "Authorization: Bearer $MIABI_TOKEN" -H 'Content-Type: application/json' -d @-
```

Ship through the pipeline from now on. A plain **Deploy** of the app would build the repository
root, which has no Dockerfile. Only a pipeline adopted from the app's own repository takes over
its deploys.

### 3. Run it

```bash
miabi pipeline ls
miabi pipeline run node-example
```

A run is pinned to one commit. `uses: deploy` is queued only once every step has passed, and it
deploys the image by digest, so what you tested is what runs.

## Triggers

```yaml
on:
  push:
    branches: [main]        # a push webhook starts a run at the pushed commit
  manual: true              # Run now, the trigger API, miabi pipeline run
  schedule: "0 3 * * *"     # cron, UTC: runs the branch head
```

For `push`, reveal the pipeline's webhook URL and secret in the console (or
`GET …/pipelines/{id}/webhook-info`) and add them to your Git host as a push webhook. A
scheduled run deploys too, when the pipeline ends in `uses: deploy`.

## Things worth knowing

- **A build step sees no `env`.** It shells out to the image build, and nothing crosses that
  boundary. Its `build-args` expand only the version variables, such as `${MIABI_VERSION}`. For
  anything else, write a file in an earlier step, as [`monorepo/go.yaml`](monorepo/go.yaml) does.
- **Versions instead of run numbers.** The `versioning:` block in [`release.yaml`](release.yaml)
  tags each successful build `v1.4.2`, picking the bump from the commit message (`fix:` patch,
  `feat:` minor, `feat!:` major) and moving `latest` along. Every successful run gets a new
  version, the nightly rebuild included. Miabi still deploys by digest. See
  [Versioning](https://docs.miabi.io/docs/cicd/versioning).
- **Pass values between steps** by appending `KEY=VALUE` to `$MIABI_ENV`.
- **Secrets** come from the workspace vault as `${{ secrets.NAME }}`, are masked in logs, and a
  missing one fails the run before it starts. [`release.yaml`](release.yaml) needs
  `SLACK_WEBHOOK`.
- **`continue-on-error: true`** records a failed step but lets the run succeed: right for scans
  and notifications you don't want to block a release.
- **Scanning the built image:** the runner gives every job `MIABI_REGISTRY_USER` and
  `MIABI_REGISTRY_TOKEN`, scoped to that job, so Trivy can pull `$MIABI_IMAGE_DIGEST`.
- **Multi-platform builds** need runner 0.0.11 or later, and a Dockerfile that cross-compiles,
  like [`go/Dockerfile`](../go/Dockerfile), avoids slow emulation.
