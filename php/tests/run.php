<?php

declare(strict_types=1);

require __DIR__.'/../src/App.php';

$failures = 0;
$check = function (string $name, bool $ok) use (&$failures): void {
    echo ($ok ? 'ok   ' : 'FAIL ').$name.PHP_EOL;
    $failures += $ok ? 0 : 1;
};

$app = new App(['APP_VERSION' => '1.2.3']);

[$status, , $body] = $app->handle('GET', '/healthz');
$check('GET /healthz answers ok', $status === 200 && json_decode($body, true) === ['status' => 'ok']);

[$status, , $body] = $app->handle('GET', '/api/info');
$info = json_decode($body, true);
$check('GET /api/info reports the version', $status === 200 && $info['version'] === '1.2.3');
$check('GET /api/info has no visits without Redis', $info['visits'] === null);
$check('GET /api/info has an empty miabi object outside Miabi', str_contains($body, '"miabi":{}'));

[$status, $type, $body] = $app->handle('GET', '/');
$check('GET / renders the page', $status === 200 && str_starts_with($type, 'text/html') && str_contains($body, 'Hello from PHP on Miabi'));

$miabiApp = new App(['MIABI_RELEASE' => '7', 'MIABI_NODE' => 'worker-2', 'MIABI_SECRET_TOKEN' => 'x']);
[, , $body] = $miabiApp->handle('GET', '/api/info');
$miabi = json_decode($body, true)['miabi'];
$check('GET /api/info lists only the allow-listed MIABI_ variables', $miabi['MIABI_RELEASE'] === '7' && !array_key_exists('MIABI_SECRET_TOKEN', $miabi));
[, , $body] = $miabiApp->handle('GET', '/');
$check('GET / names the release and node', str_contains($body, '<p>Release 7 on node worker-2</p>'));

[$status] = $app->handle('GET', '/missing');
$check('unknown paths are 404', $status === 404);

[$status] = $app->handle('POST', '/healthz');
$check('wrong method is 404', $status === 404);

exit($failures === 0 ? 0 : 1);
