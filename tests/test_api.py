import importlib.util
import sys

import pytest

from conftest import ROOT

fastapi = pytest.importorskip("fastapi")
pytest.importorskip("hermes_constants")
from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402


@pytest.fixture
def client(hermes_home, monkeypatch):
    monkeypatch.setenv("HERMES_HOME", str(hermes_home))
    monkeypatch.setenv("ARK_PASSPHRASE", "api-test-passphrase")
    for mod in [m for m in sys.modules if m.startswith(("hermes_ark_desktop_backend", "ark_api_under_test"))]:
        del sys.modules[mod]
    spec = importlib.util.spec_from_file_location("ark_api_under_test", ROOT / "dashboard" / "plugin_api.py")
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    app = FastAPI()
    app.include_router(api.router, prefix="/api/plugins/hermes-ark")
    c = TestClient(app)
    c.pkg = api._pkg()
    c.pkg.settings._cache.clear()
    return c


B = "/api/plugins/hermes-ark"


def test_status_before_init(client):
    r = client.get(f"{B}/status").json()
    assert r["state"] == "not_initialized" and r["snapshots"] == 0


def test_full_desktop_flow(client, hermes_home):
    client.pkg.cli.op_init(no_encryption=False)
    assert client.get(f"{B}/status").json()["state"] == "ready"
    r = client.post(f"{B}/snapshot", json={"label": "first"}).json()
    assert r["ok"], r
    (hermes_home / "memories" / "MEMORY.md").write_text("")
    assert client.post(f"{B}/snapshot", json={"label": "after wipe"}).json()["ok"]

    snaps = client.get(f"{B}/snapshots").json()["snapshots"]
    assert [s["label"] for s in snaps] == ["after wipe", "first"]          # newest first
    first = snaps[1]["id"]

    d = client.get(f"{B}/snapshots/{first}").json()
    kinds = {(g["profile"], g["kind"]) for g in d["groups"]}
    assert ("default", "memory") in kinds and ("work", "memory") in kinds
    skill = next(g for g in d["groups"] if g["kind"] == "skill")
    assert skill["items"] == ["weather"]

    diff = client.get(f"{B}/diff", params={"new": snaps[0]["id"]}).json()
    assert diff["old"] == first and any(c["path"] == "memories/MEMORY.md" and c["status"] == "modified" for c in diff["changes"])

    prev = client.post(f"{B}/restore/preview", json={"what": "memory", "snapshot": first}).json()
    assert [w["path"] for w in prev["write"]] == ["memories/MEMORY.md"] and prev["blocked"] is None
    assert client.post(f"{B}/restore", json={"what": "memory", "snapshot": first}).status_code == 400   # confirm required
    r = client.post(f"{B}/restore", json={"what": "memory", "snapshot": first, "confirm": True}).json()
    assert r["ok"] and "Restored 1 file" in r["message"]
    assert "Hermes Ark" in (hermes_home / "memories" / "MEMORY.md").read_text()

    assert client.post(f"{B}/verify", json={}).json()["ok"]
    assert client.get(f"{B}/status").json()["last_verify"]["ok"] is True


def test_sessions_restore_is_blocked_from_the_app(client, hermes_home):
    client.pkg.cli.op_init(no_encryption=False)
    client.post(f"{B}/snapshot", json={"label": "x"})
    prev = client.post(f"{B}/restore/preview", json={"what": "sessions"}).json()
    import sqlite3
    db = sqlite3.connect(hermes_home / "state.db")
    db.execute("delete from messages")
    db.commit()
    db.close()
    prev = client.post(f"{B}/restore/preview", json={"what": "sessions"}).json()
    assert prev["blocked"] == "sessions_need_stopped_gateway"
    r = client.post(f"{B}/restore", json={"what": "sessions", "confirm": True}).json()
    assert not r["ok"] and "stopped" in r["message"]


def test_bad_input_is_a_clean_400(client):
    client.pkg.cli.op_init(no_encryption=False)
    client.post(f"{B}/snapshot", json={})
    assert client.get(f"{B}/snapshots/nope").status_code == 400
    assert client.post(f"{B}/restore/preview", json={"what": "file:../../etc/passwd"}).status_code == 400
    assert client.post(f"{B}/restore/preview", json={"what": "bogus"}).status_code == 400
