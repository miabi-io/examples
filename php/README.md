# PHP example

Plain PHP 8.4, without a framework, on
[`jkaninda/nginx-php-fpm`](https://github.com/jkaninda/nginx-php-fpm). The image runs nginx and
PHP-FPM under supervisor and serves `/var/www/html/public` on port 80, so `public/index.php` is
the only entry point, and `src/` and `tests/` are never reachable over HTTP.

| Route | |
|---|---|
| `/` | A page naming the version, the container, and the visit count |
| `/healthz` | `{"status":"ok"}`, the health check |
| `/api/info` | Version, hostname, PHP version, visits |

`/api/info` also returns a `miabi` object with what Miabi set in the container, such as
`MIABI_RELEASE`, `MIABI_NODE` and `MIABI_REPLICA`; outside Miabi it is empty.

With `REDIS_URL` set, each `/` and `/api/info` request increments a counter in Redis. The
[manifest](../gitops/php/app.yaml) declares a managed Redis and passes its URL in:

```yaml
env:
  REDIS_URL: "{{ .databases.php-example-redis.uri }}"
```

## Run it

```bash
php tests/run.php
docker build -t php-example . && docker run --rm -p 8080:80 php-example
```

## Deploy it

- **From an image:** `miabi apply -f ../gitops/php/app.yaml`.
- **From source:** copy this folder into its own repository and create a Git app from it.
  Miabi offers to adopt [`.miabi/pipeline.yaml`](.miabi/pipeline.yaml), which lints every file
  and runs the tests before the build.

## Customising nginx

The image copies `conf/nginx/nginx-site.conf` over its default server block when the file
exists. Add one to change the document root, upload limits or caching.
