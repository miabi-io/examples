<?php

declare(strict_types=1);

/**
 * App routes a request to a response. It has no framework dependency so the example stays
 * readable; public/index.php is the only entry point nginx sends PHP requests to.
 */
final class App
{
    // An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
    private const MIABI_VARS = [
        'MIABI_APP_NAME',
        'MIABI_WORKSPACE_NAME',
        'MIABI_STACK',
        'MIABI_RELEASE',
        'MIABI_VERSION',
        'MIABI_COMMIT',
        'MIABI_LOCATION',
        'MIABI_NODE',
        'MIABI_REPLICA',
        'MIABI_PUBLIC_URL',
    ];

    /** @param array<string, string> $env */
    public function __construct(private array $env, private ?Redis $redis = null)
    {
    }

    /** @return array{0: int, 1: string, 2: string} status, content type, body */
    public function handle(string $method, string $path): array
    {
        return match ([$method, $path]) {
            ['GET', '/healthz'] => $this->json(200, ['status' => 'ok']),
            ['GET', '/api/info'] => $this->json(200, $this->info()),
            ['GET', '/'] => [200, 'text/html; charset=utf-8', $this->page()],
            default => $this->json(404, ['error' => 'not found']),
        };
    }

    /** @return array<string, mixed> */
    private function info(): array
    {
        return [
            'app' => $this->env['APP_NAME'] ?? $this->env['MIABI_APP_NAME'] ?? 'php-example',
            'version' => $this->env['APP_VERSION'] ?? 'dev',
            'hostname' => gethostname(),
            'php' => PHP_VERSION,
            'visits' => $this->countVisit(),
            'miabi' => (object) $this->miabi(),
        ];
    }

    /** @return array<string, string> */
    private function miabi(): array
    {
        return array_filter(
            array_intersect_key($this->env, array_flip(self::MIABI_VARS)),
            static fn (string $v): bool => $v !== '',
        );
    }

    private function countVisit(): ?int
    {
        try {
            return $this->redis?->incr('php-example:visits');
        } catch (RedisException) {
            return null;
        }
    }

    private function page(): string
    {
        $i = $this->info();
        $h = static fn (mixed $v): string => htmlspecialchars((string) $v, ENT_QUOTES);
        $visits = $i['visits'] === null ? 'no Redis configured' : $h($i['visits']).' visits (counted in Redis)';
        $m = $this->miabi();
        $release = '';
        if (isset($m['MIABI_RELEASE'])) {
            $line = 'Release '.$m['MIABI_RELEASE']
                .(isset($m['MIABI_NODE']) ? ' on node '.$m['MIABI_NODE'] : '')
                .(isset($m['MIABI_REPLICA']) ? ', replica '.$m['MIABI_REPLICA'] : '');
            $release = "\n<p>{$h($line)}</p>";
        }

        return <<<HTML
        <!doctype html>
        <html lang="en">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{$h($i['app'])}</title>
        <style>body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}code{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}</style></head>
        <body>
        <h1>Hello from PHP on Miabi</h1>
        <p>Version <code>{$h($i['version'])}</code> · PHP {$h($i['php'])} · served by <code>{$h($i['hostname'])}</code></p>{$release}
        <p>{$visits}</p>
        <p>Try <a href="/api/info">/api/info</a> or <a href="/healthz">/healthz</a>.</p>
        </body>
        </html>
        HTML;
    }

    /** @return array{0: int, 1: string, 2: string} */
    private function json(int $status, array $body): array
    {
        return [$status, 'application/json', json_encode($body, JSON_THROW_ON_ERROR)];
    }

    /** connectRedis returns a client for REDIS_URL (redis://:password@host:port), or null when unset. */
    public static function connectRedis(?string $url): ?Redis
    {
        if (!$url || !extension_loaded('redis')) {
            return null;
        }
        $parts = parse_url($url);
        $redis = new Redis();
        try {
            $redis->connect($parts['host'], $parts['port'] ?? 6379, 2.0);
            if (isset($parts['pass'])) {
                $redis->auth(isset($parts['user']) && $parts['user'] !== '' ? [urldecode($parts['user']), urldecode($parts['pass'])] : urldecode($parts['pass']));
            }
        } catch (RedisException $e) {
            error_log('redis: '.$e->getMessage());
            return null;
        }
        return $redis;
    }
}
