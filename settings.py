"""Bridge between Hermes (settings, secrets, home dir) and the pure ``arkcore`` engine."""

from __future__ import annotations

import hashlib
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Optional

from .arkcore.backends import LocalBackend
from .arkcore.repo import Repository

PLUGIN_NAME = "hermes-ark"

_get_config: Callable[[str, Any], Any] = lambda key, default=None: default  # replaced in register()


def bind(ctx) -> None:
    global _get_config
    _get_config = ctx.get_config


def hermes_home() -> Path:
    from hermes_constants import get_hermes_home
    return Path(get_hermes_home())


def data_dir() -> Path:
    from plugins.plugin_storage import plugin_data_dir
    return plugin_data_dir(PLUGIN_NAME)


def passphrase() -> Optional[str]:
    from agent.secret_scope import get_secret
    try:
        value = get_secret("ARK_PASSPHRASE")
    except Exception:  # multiplexed gateway with no scope: fail closed, behave as unset
        value = None
    return value or None


def get(key: str, default: Any = None) -> Any:
    try:
        value = _get_config(key, default)
    except Exception:
        return default
    return default if value is None else value


@dataclass
class Settings:
    repository: Path
    include_secrets: bool
    exclude: list[str]
    auto: bool = True
    interval_minutes: int = 60
    health_guard: bool = True


def _int(value: Any, default: int) -> int:
    try:
        return max(1, int(value))
    except (TypeError, ValueError):
        return default


def load() -> Settings:
    repo = str(get("repository", "") or "").strip()
    excl = get("exclude", []) or []
    return Settings(
        repository=Path(repo).expanduser() if repo else data_dir() / "repo",
        include_secrets=bool(get("include_secrets", False)),
        exclude=[str(e) for e in excl if str(e).strip()],
        auto=bool(get("auto_snapshots", True)),
        interval_minutes=_int(get("interval_minutes", 60), 60),
        health_guard=bool(get("health_guard", True)),
    )


def lock_path(settings: Optional[Settings] = None) -> Path:
    return data_dir() / "repo.lock"


def backend(settings: Optional[Settings] = None) -> LocalBackend:
    return LocalBackend((settings or load()).repository)


_cache: dict[tuple[str, str], Repository] = {}
_cache_lock = threading.Lock()


def open_repo(settings: Optional[Settings] = None) -> Repository:
    """Open (and cache) the repository. Unlocking runs scrypt (~0.3 s), so automatic snapshots
    reuse the unlocked repository instead of paying that on every memory write."""
    st = settings or load()
    pw = passphrase()
    key = (str(st.repository), hashlib.sha256((pw or "").encode()).hexdigest())
    with _cache_lock:
        repo = _cache.get(key)
        if repo is None or not Repository.exists(repo.backend):
            repo = Repository.open(backend(st), pw)
            _cache.clear()
            _cache[key] = repo
        return repo
