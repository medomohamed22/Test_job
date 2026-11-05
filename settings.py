"""Bridge between Hermes (settings, secrets, home dir) and the pure ``arkcore`` engine."""

from __future__ import annotations

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


@dataclass
class Settings:
    repository: Path
    include_secrets: bool
    exclude: list[str]


def load() -> Settings:
    repo = str(_get_config("repository", "") or "").strip()
    excl = _get_config("exclude", []) or []
    return Settings(
        repository=Path(repo).expanduser() if repo else data_dir() / "repo",
        include_secrets=bool(_get_config("include_secrets", False)),
        exclude=[str(e) for e in excl if str(e).strip()],
    )


def backend(settings: Optional[Settings] = None) -> LocalBackend:
    return LocalBackend((settings or load()).repository)


def open_repo(settings: Optional[Settings] = None) -> Repository:
    return Repository.open(backend(settings), passphrase())
