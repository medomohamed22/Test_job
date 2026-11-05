import sqlite3
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))  # makes the pure engine importable as ``arkcore``

from arkcore import crypto  # noqa: E402

# Keep scrypt cheap in tests; production uses crypto.DEFAULT_KDF.
crypto.DEFAULT_KDF = {"name": "scrypt", "n": 2**10, "r": 8, "p": 1}


@pytest.fixture
def hermes_home(tmp_path: Path) -> Path:
    """A realistic little Hermes home."""
    h = tmp_path / "home"
    (h / "memories").mkdir(parents=True)
    (h / "memories" / "MEMORY.md").write_text("- User is building Hermes Ark\n- Likes Arabic answers\n")
    (h / "memories" / "USER.md").write_text("Name: Medo\n")
    (h / "SOUL.md").write_text("You are a kind, precise assistant.\n")
    (h / "config.yaml").write_text("model: test\n")
    (h / ".env").write_text("OPENROUTER_API_KEY=sk-secret\n")
    (h / "auth.json").write_text('{"token": "secret"}')
    (h / "skills" / "weather").mkdir(parents=True)
    (h / "skills" / "weather" / "SKILL.md").write_text("# Weather\nUse wttr.in\n")
    (h / "cron").mkdir()
    (h / "cron" / "jobs.json").write_text('{"jobs": []}')
    (h / "logs").mkdir()
    (h / "logs" / "agent.log").write_text("noise\n")
    (h / "cache" / "images").mkdir(parents=True)
    (h / "cache" / "images" / "cat.png").write_bytes(b"\x89PNG fake")
    (h / "cache" / "models_dev_cache.json").write_text("{}")
    (h / "skills" / "weather" / "__pycache__").mkdir()
    (h / "skills" / "weather" / "__pycache__" / "x.pyc").write_bytes(b"x")
    (h / "profiles" / "work" / "memories").mkdir(parents=True)
    (h / "profiles" / "work" / "memories" / "MEMORY.md").write_text("- Work profile memory\n")
    (h / "profiles" / "work" / ".env").write_text("K=v\n")
    db = sqlite3.connect(h / "state.db")
    db.execute("pragma journal_mode=wal")
    db.execute("create table messages(id integer primary key, body text)")
    db.executemany("insert into messages(body) values (?)", [(f"message {i}",) for i in range(200)])
    db.commit()
    db.close()
    return h
