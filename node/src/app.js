import os from 'node:os';
import express from 'express';

// An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
const MIABI_VARS = [
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

const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function releaseLine(m) {
  if (!m.MIABI_RELEASE) return '';
  let line = `Release ${m.MIABI_RELEASE}`;
  if (m.MIABI_NODE) line += ` on node ${m.MIABI_NODE}`;
  if (m.MIABI_REPLICA) line += `, replica ${m.MIABI_REPLICA}`;
  return `\n<p>${escapeHtml(line)}</p>`;
}

export function createApp(env = process.env) {
  const app = express();
  const info = () => ({
    app: env.APP_NAME || env.MIABI_APP_NAME || 'node-example',
    version: env.APP_VERSION || 'dev',
    hostname: os.hostname(),
    node: process.version,
    uptimeSeconds: Math.round(process.uptime()),
    miabi: Object.fromEntries(MIABI_VARS.filter((k) => env[k]).map((k) => [k, env[k]])),
  });

  app.disable('x-powered-by');
  app.set('trust proxy', true);

  app.get('/healthz', (_req, res) => res.json({ status: 'ok' }));

  app.get('/api/info', (_req, res) => res.json(info()));

  // UPSTREAM_URL is set from {{ .applications.<name> }} in the GitOps full-stack example.
  app.get('/api/upstream', async (_req, res) => {
    if (!env.UPSTREAM_URL) {
      return res.status(404).json({ error: 'UPSTREAM_URL is not set' });
    }
    try {
      const upstream = await fetch(new URL('/api/info', env.UPSTREAM_URL), {
        signal: AbortSignal.timeout(3000),
      });
      res.status(upstream.status).json({ upstream: env.UPSTREAM_URL, body: await upstream.json() });
    } catch (err) {
      res.status(502).json({ upstream: env.UPSTREAM_URL, error: err.message });
    }
  });

  app.get('/', (req, res) => {
    const i = info();
    res.type('html').send(`<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${i.app}</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}code{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}</style></head>
<body>
<h1>Hello from Node.js on Miabi</h1>
<p>Version <code>${i.version}</code> · ${i.node} · served by <code>${i.hostname}</code></p>${releaseLine(i.miabi)}
<p>You reached it over <code>${req.protocol}</code>. Try <a href="/api/info">/api/info</a> or <a href="/healthz">/healthz</a>.</p>
</body>
</html>`);
  });

  return app;
}
