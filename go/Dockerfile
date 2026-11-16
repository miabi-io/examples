# syntax=docker/dockerfile:1
ARG GO_VERSION=1.25

# Runs on the build machine's platform and cross-compiles, so multi-arch builds need no emulation.
FROM --platform=$BUILDPLATFORM golang:${GO_VERSION}-alpine AS build
ARG TARGETOS TARGETARCH
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
# .version is written by the pipeline's version step; local builds fall back to VERSION.
RUN version="$(cat .version 2>/dev/null || cat VERSION)" \
 && CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags "-s -w -X main.version=${version}" -o /out/server . \
 && mkdir -p /out/data

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/server /server
# Docker copies this ownership into an empty volume mounted here, so the nonroot user can write it.
COPY --from=build --chown=65532:65532 /out/data /data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=3 CMD ["/server", "healthcheck"]
ENTRYPOINT ["/server"]
