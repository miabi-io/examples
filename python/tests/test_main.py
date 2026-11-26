import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client(monkeypatch):
    monkeypatch.delenv("DB_URL", raising=False)
    monkeypatch.setenv("APP_VERSION", "1.2.3")
    with TestClient(app) as c:
        yield c


def test_healthz(client):
    assert client.get("/healthz").json() == {"status": "ok"}


def test_info_reports_version_and_database(client):
    body = client.get("/api/info").json()
    assert body["version"] == "1.2.3"
    assert body["database"] is False


def test_info_lists_only_allowed_miabi_vars(client, monkeypatch):
    monkeypatch.setenv("MIABI_RELEASE", "7")
    monkeypatch.setenv("MIABI_NODE", "worker-2")
    monkeypatch.setenv("MIABI_SECRET_TOKEN", "x")
    miabi = client.get("/api/info").json()["miabi"]
    assert miabi["MIABI_RELEASE"] == "7"
    assert "MIABI_SECRET_TOKEN" not in miabi
    assert "<p>Release 7 on node worker-2</p>" in client.get("/").text


def test_index_renders(client):
    res = client.get("/")
    assert res.status_code == 200
    assert "Hello from Python on Miabi" in res.text


def test_notes_need_a_database(client):
    assert client.get("/api/notes").status_code == 503


def test_note_validation(client):
    assert client.post("/api/notes", json={"body": ""}).status_code == 422
