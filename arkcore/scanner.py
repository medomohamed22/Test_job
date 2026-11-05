"""Decide which files under a Hermes home belong in a snapshot.

The rules mirror Hermes' own ``hermes backup`` exclusions (regenerable runtimes, caches, browser
profiles, nested backups) and add one policy of Ark's own: **credentials are never captured unless
the user opts in and the repository is encrypted** (``.env``, ``auth.json``, vault keys, secret
caches, pairing tokens, browser login stores).
"""

from __future__ import annotations

import os
import stat
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable, Iterator, Optional

# Anywhere in the tree: never user data.
EXCLUDED_DIRS_ANYWHERE = frozenset({
    "__pycache__", ".git", "node_modules", ".venv", "venv", "site-packages",
    ".cache", ".tox", ".nox", ".pytest_cache", ".mypy_cache", ".ruff_cache",
    "browser-profiles", "browser-profile",  # live CDP profiles / copied Cookies + Login Data
})

# Only at a home root (``~/.hermes`` or ``~/.hermes/profiles/<name>``): regenerable machine state.
EXCLUDED_ROOT_DIRS = frozenset({
    "hermes-agent",          # the code checkout - reinstall instead
    "models", "runtimes", "node", "installs", "tools",  # downloaded runtimes, often tens of GB
    "backups", "state-snapshots", "checkpoints",          # Hermes' own backups / per-session caches
    "browser_profiles",      # Browser Use CLI user-data (Login Data / Cookies)
    "logs", "audio_cache", "image_cache",                # regenerable
    "plugin-data/hermes-ark",  # never back up our own repository into itself
    # Gateway runtime state: heartbeats, lifecycle, delivery queues' locks, update-check caches.
    "state", "runtime", "plugin-update-checks", "source-checks",
    # The Webapp's signed-in session token: a credential, and stale on any other machine.
    "webapp",
})

# Root-level files that are machine state or regenerable caches, never user data.
EXCLUDED_ROOT_FILES = frozenset({
    "install_id",                          # this machine's identity; must not be cloned onto another
    "gateway_state.json", "gateway.sock.path", "gateway-starts.log",
    "models_dev_cache.json", "models_dev_cache.etag", "provider_models_cache.json",
})
# Inside cron/: the scheduler's own liveness markers.
EXCLUDED_CRON_FILES = frozenset({"ticker_heartbeat", "ticker_last_success"})

# ``cache/`` at a home root: only durable media / evidence the user cannot regenerate.
KEPT_CACHE_SUBDIRS = frozenset({"images", "audio", "videos", "documents", "screenshots", "citations", "generated"})

# Credential stores (relative to a home root). Captured only with include_secrets=True.
SECRET_ROOT_FILES = frozenset({
    ".env", "auth.json", "vault.key", "vault.json.enc",
    "bws_cache.json", "bws_cache.enc.json", "op_cache.json", "photon-sidecar.json",
})
SECRET_ROOT_DIRS = frozenset({"pairing"})

SQLITE_SIDECARS = ("-wal", "-shm", "-journal")
# Runtime files that must never be captured — and therefore never deleted by a restore while
# another process may hold them (e.g. the memory tool's MEMORY.md.lock).
RUNTIME_SUFFIXES = (".lock", ".pid", ".sock")
RUNTIME_PREFIXES = (".ark-", ".tmp-")


@dataclass(frozen=True)
class FileEntry:
    rel: str          # POSIX path relative to the backed-up root, e.g. "profiles/work/skills/x/SKILL.md"
    path: Path        # absolute path on disk
    size: int
    mtime_ns: int
    mode: int
    secret: bool = False

    @property
    def is_sqlite(self) -> bool:
        return self.rel.endswith(".db")


def _home_roots(root: Path) -> list[tuple[str, Path]]:
    """The main home plus every profile home under ``profiles/``: (rel prefix, path)."""
    roots = [("", root)]
    prof = root / "profiles"
    if prof.is_dir():
        for child in sorted(prof.iterdir()):
            if child.is_dir() and not child.is_symlink():
                roots.append((f"profiles/{child.name}/", child))
    return roots


def _excluded_under_home(rel_in_home: PurePosixPath, is_dir: bool) -> Optional[str]:
    """Reason a path relative to a home root is excluded, or None to keep it."""
    parts = rel_in_home.parts
    if not parts:
        return None
    if any(p in EXCLUDED_DIRS_ANYWHERE for p in (parts if is_dir else parts[:-1])):
        return "regenerable"
    top = parts[0]
    joined2 = "/".join(parts[:2])
    if top in EXCLUDED_ROOT_DIRS or joined2 in EXCLUDED_ROOT_DIRS:
        return "regenerable"
    if top == "cache" and (len(parts) == 1 and not is_dir or len(parts) >= 2 and parts[1] not in KEPT_CACHE_SUBDIRS):
        return "regenerable"
    if top == "profiles":
        return "profile-root"  # walked separately as its own home
    name = parts[-1]
    if not is_dir and len(parts) == 1 and name in EXCLUDED_ROOT_FILES:
        return "regenerable"
    if not is_dir and len(parts) == 2 and top == "cron" and name in EXCLUDED_CRON_FILES:
        return "runtime"
    if not is_dir and any(name.endswith(s) for s in SQLITE_SIDECARS):
        return "sqlite-sidecar"
    if not is_dir and (name.endswith(RUNTIME_SUFFIXES) or name.startswith(RUNTIME_PREFIXES)):
        return "runtime"
    return None


def _is_secret(rel_in_home: PurePosixPath) -> bool:
    parts = rel_in_home.parts
    return (len(parts) == 1 and parts[0] in SECRET_ROOT_FILES) or (parts and parts[0] in SECRET_ROOT_DIRS)


def scan(root: Path | str, include_secrets: bool = False, extra_excludes: Iterable[str] = ()) -> Iterator[FileEntry]:
    """Yield every file to capture under *root* (a Hermes home), in a stable order.

    Symlinks, sockets and devices are skipped (a zip/restore cannot hold them faithfully, and a
    symlink could pull files from outside the home into a backup).
    """
    root = Path(root)
    extra = tuple(e.strip("/") for e in extra_excludes if e.strip("/"))
    for prefix, home in _home_roots(root):
        for dirpath, dirnames, filenames in os.walk(home, followlinks=False):
            d = Path(dirpath)
            rel_dir = PurePosixPath(d.relative_to(home).as_posix()) if d != home else PurePosixPath()
            keep_dirs = []
            for name in sorted(dirnames):
                child = d / name
                rel = rel_dir / name
                if child.is_symlink():
                    continue
                if _excluded_under_home(rel, True):
                    continue
                if rel.parts[:1] == ("desktop-plugins",) and len(rel.parts) == 2 and (child / ".hermes-package.json").exists():
                    continue  # Hermes regenerates package desktop halves; hand-written desktop plugins are kept
                if _is_secret(rel) and not include_secrets:
                    continue
                if any((prefix + rel.as_posix()).startswith(e) for e in extra):
                    continue
                keep_dirs.append(name)
            dirnames[:] = keep_dirs
            for name in sorted(filenames):
                rel = rel_dir / name
                full_rel = prefix + rel.as_posix()
                if _excluded_under_home(rel, False):
                    continue
                secret = _is_secret(rel)
                if secret and not include_secrets:
                    continue
                if any(full_rel == e or full_rel.startswith(e + "/") for e in extra):
                    continue
                p = d / name
                try:
                    st = p.lstat()
                except OSError:
                    continue
                if not stat.S_ISREG(st.st_mode):
                    continue
                yield FileEntry(full_rel, p, st.st_size, st.st_mtime_ns, stat.S_IMODE(st.st_mode), secret)


# ---- Classification: what a path *is*, so users can restore "my memory" instead of file paths ----

def classify(rel: str) -> tuple[str, str, str]:
    """Return (profile, kind, item) for a snapshot path.

    kind is one of memory, soul, skill, config, cron, sessions, plugin, secrets, media, other.
    item names the restorable unit (a skill's folder name, a plugin name...), or "" for the kind.
    """
    parts = PurePosixPath(rel).parts
    profile = "default"
    if len(parts) >= 3 and parts[0] == "profiles":
        profile, parts = parts[1], parts[2:]
    if not parts:
        return profile, "other", ""
    top, name = parts[0], parts[-1]
    if len(parts) == 1:
        if name in ("MEMORY.md", "USER.md"):
            return profile, "memory", name
        if name == "SOUL.md":
            return profile, "soul", ""
        if name == "config.yaml":
            return profile, "config", ""
        if name == "state.db":
            return profile, "sessions", ""
        if name in SECRET_ROOT_FILES:
            return profile, "secrets", name
        return profile, "other", name
    if top == "memories":
        return profile, "memory", parts[1] if len(parts) > 1 else ""
    if top == "skills":
        return profile, "skill", parts[1]
    if top == "cron":
        return profile, "cron", ""
    if top == "sessions":
        return profile, "sessions", ""
    if top == "plugins":
        return profile, "plugin", parts[1]
    if top in SECRET_ROOT_DIRS:
        return profile, "secrets", top
    if top == "cache":
        return profile, "media", parts[1] if len(parts) > 1 else ""
    return profile, "other", top
