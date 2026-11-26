"""A small FastAPI service deployed on Miabi, with an optional Postgres-backed notes API."""

import html
import os
import platform
import socket
import time
from contextlib import asynccontextmanager

import psycopg
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

STARTED = time.monotonic()

# An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
MIABI_VARS = (
    "MIABI_APP_NAME",
    "MIABI_WORKSPACE_NAME",
    "MIABI_STACK",
    "MIABI_RELEASE",
    "MIABI_VERSION",
    "MIABI_COMMIT",
    "MIABI_LOCATION",
    "MIABI_NODE",
    "MIABI_REPLICA",
    "MIABI_PUBLIC_URL",
)


def database_url() -> str | None:
    return os.environ.get("DB_URL") or None


def connect() -> psycopg.Connection:
    url = database_url()
    if url is None:
        raise HTTPException(status_code=503, detail="DB_URL is not set")
    return psycopg.connect(url, connect_timeout=5)


@asynccontextmanager
async def lifespan(_: FastAPI):
    if database_url():
        with connect() as conn:
            conn.execute(
                """CREATE TABLE IF NOT EXISTS notes (
                       id         BIGSERIAL PRIMARY KEY,
                       body       TEXT NOT NULL,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                   )"""
            )
    yield


app = FastAPI(title="python-example", lifespan=lifespan)


class NoteIn(BaseModel):
    body: str = Field(min_length=1, max_length=500)


class Note(NoteIn):
    id: int
    created_at: str


def miabi_env() -> dict[str, str]:
    return {k: v for k in MIABI_VARS if (v := os.environ.get(k))}


def release_line(miabi: dict[str, str]) -> str:
    if "MIABI_RELEASE" not in miabi:
        return ""
    line = f"Release {miabi['MIABI_RELEASE']}"
    if "MIABI_NODE" in miabi:
        line += f" on node {miabi['MIABI_NODE']}"
    if "MIABI_REPLICA" in miabi:
        line += f", replica {miabi['MIABI_REPLICA']}"
    return f"\n<p>{html.escape(line)}</p>"


def info() -> dict:
    return {
        "app": os.environ.get("APP_NAME", os.environ.get("MIABI_APP_NAME", "python-example")),
        "version": os.environ.get("APP_VERSION", "dev"),
        "hostname": socket.gethostname(),
        "python": platform.python_version(),
        "database": database_url() is not None,
        "uptimeSeconds": int(time.monotonic() - STARTED),
        "miabi": miabi_env(),
    }


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.get("/api/info")
def api_info() -> dict:
    return info()


@app.get("/api/notes")
def list_notes() -> list[Note]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT id, body, created_at FROM notes ORDER BY id DESC LIMIT 50"
        ).fetchall()
    return [Note(id=r[0], body=r[1], created_at=r[2].isoformat()) for r in rows]


@app.post("/api/notes", status_code=201)
def create_note(note: NoteIn) -> Note:
    with connect() as conn:
        row = conn.execute(
            "INSERT INTO notes (body) VALUES (%s) RETURNING id, body, created_at",
            (note.body,),
        ).fetchone()
    return Note(id=row[0], body=row[1], created_at=row[2].isoformat())


@app.get("/", response_class=HTMLResponse)
def index() -> str:
    i = info()
    db = "connected to Postgres" if i["database"] else "running without a database"
    return f"""<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{i['app']}</title>
<style>body{{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}}code{{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}}</style></head>
<body>
<h1>Hello from Python on Miabi</h1>
<p>Version <code>{i['version']}</code> · Python {i['python']} · {db} · served by <code>{i['hostname']}</code></p>{release_line(i['miabi'])}
<p>Try <a href="/api/info">/api/info</a>, <a href="/api/notes">/api/notes</a> or the OpenAPI docs at <a href="/docs">/docs</a>.</p>
</body>
</html>"""
