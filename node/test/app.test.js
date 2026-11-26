import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createApp } from '../src/app.js';

let server;
let base;

before(async () => {
  server = createApp({ APP_VERSION: '1.2.3' }).listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => server.close());

test('GET /healthz answers ok', async () => {
  const res = await fetch(`${base}/healthz`);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { status: 'ok' });
});

test('GET /api/info reports the version', async () => {
  const body = await (await fetch(`${base}/api/info`)).json();
  assert.equal(body.version, '1.2.3');
  assert.ok(body.hostname);
});

test('GET / renders the page', async () => {
  const res = await fetch(base);
  assert.equal(res.status, 200);
  assert.match(await res.text(), /Hello from Node.js on Miabi/);
});

test('GET /api/upstream is 404 without UPSTREAM_URL', async () => {
  const res = await fetch(`${base}/api/upstream`);
  assert.equal(res.status, 404);
});

test('GET /api/info lists only the allow-listed MIABI_ variables', async () => {
  const env = { MIABI_RELEASE: '7', MIABI_NODE: 'worker-2', MIABI_SECRET_TOKEN: 'x' };
  const srv = createApp(env).listen(0);
  await new Promise((resolve) => srv.once('listening', resolve));
  try {
    const url = `http://127.0.0.1:${srv.address().port}`;
    const body = await (await fetch(`${url}/api/info`)).json();
    assert.equal(body.miabi.MIABI_RELEASE, '7');
    assert.ok(!('MIABI_SECRET_TOKEN' in body.miabi));
    assert.match(await (await fetch(url)).text(), /Release 7 on node worker-2</);
  } finally {
    srv.close();
  }
});

test('GET /api/info has an empty miabi object outside Miabi', async () => {
  const body = await (await fetch(`${base}/api/info`)).json();
  assert.deepEqual(body.miabi, {});
});
