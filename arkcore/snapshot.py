"""Create, list and compare snapshots of a Hermes home."""

from __future__ import annotations

import os
import socket
import sqlite3
import tempfile
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable, Optional

from .repo import Repository, new_snapshot_id
from .scanner import FileEntry, classify, scan, skill_roots

CHUNK = 1 << 20          # 1 MiB chunks: SQLite pages stay aligned, so an edited DB re-uploads only touched chunks
SMALL = 4 << 20          # files up to 4 MiB are one blob


@dataclass
class SnapshotResult:
    snapshot_id: str
    files: int
    bytes_total: int
    bytes_new: int
    blobs_new: int
    seconds: float
    skipped: list[str] = field(default_factory=list)
    unchanged: bool = False


def _read_consistent(entry: FileEntry) -> bytes:
    """Read a file; SQLite databases are copied through sqlite3's online backup API so a live,
    WAL-mode database yields a consistent image (never a torn read)."""
    if entry.is_sqlite:
        with entry.path.open("rb") as f:
            is_sqlite = f.read(16) == b"SQLite format 3\x00"
        if is_sqlite:
            fd, tmp = tempfile.mkstemp(suffix=".db")
            os.close(fd)
            try:
                src = sqlite3.connect(f"file:{entry.path}?mode=ro", uri=True, timeout=10)
                dst = sqlite3.connect(tmp)
                try:
                    src.backup(dst)
                finally:
                    dst.close()
                    src.close()
                return Path(tmp).read_bytes()
            finally:
                try:
                    os.unlink(tmp)
                except OSError:
                    pass
    return entry.path.read_bytes()


def _chunks(data: bytes) -> Iterable[bytes]:
    if len(data) <= SMALL:
        yield data
        return
    for i in range(0, len(data), CHUNK):
        yield data[i:i + CHUNK]


def create_snapshot(
    repo: Repository,
    root: Path | str,
    *,
    label: str = "",
    trigger: str = "manual",
    include_secrets: bool = False,
    extra_excludes: Iterable[str] = (),
    skip_if_unchanged: bool = False,
    scope: Optional[Iterable[str]] = None,
    progress: Optional[Callable[[str], None]] = None,
) -> SnapshotResult:
    """Capture *root* into *repo*. Credentials are refused unless the repo is encrypted.

    *scope* limits the snapshot to some kinds (e.g. ``BRAIN``: memory, soul, skills, config, cron) —
    fast enough to run before every memory/skill write. A scoped snapshot records its scope, and a
    restore from it never touches anything outside that scope.
    """
    if include_secrets and not repo.encrypted:
        raise ValueError("refusing to store credentials in an unencrypted repository")
    started = time.monotonic()
    root = Path(root)
    scope_set = frozenset(scope) if scope is not None else None
    entries, skipped = [], []
    total = new_bytes = new_blobs = 0
    found = list(scan(root, include_secrets=include_secrets, extra_excludes=extra_excludes))
    roots = skill_roots(e.rel for e in found)
    for entry in found:
        if scope_set is not None and classify(entry.rel, roots)[1] not in scope_set:
            continue
        try:
            data = _read_consistent(entry)
        except (OSError, sqlite3.Error) as exc:
            skipped.append(f"{entry.rel}: {exc.__class__.__name__}")
            continue
        ids = []
        for chunk in _chunks(data):
            blob_id, fresh = repo.put_blob(chunk)
            ids.append(blob_id)
            if fresh:
                new_blobs += 1
                new_bytes += len(chunk)
        total += len(data)
        profile, kind, item = classify(entry.rel, roots)
        entries.append({"p": entry.rel, "s": len(data), "m": entry.mode, "t": entry.mtime_ns, "b": ids,
                        "k": kind, "i": item, "pr": profile, **({"x": 1} if entry.secret else {})})
        if progress:
            progress(entry.rel)

    if skip_if_unchanged:
        prev = _latest_covering(repo, scope_set)
        if prev is not None:
            prev_files = [f for f in prev["files"] if scope_set is None or f.get("k") in scope_set]
            if _content_key(prev_files) == _content_key(entries):
                return SnapshotResult(prev["id"], len(entries), total, 0, 0, time.monotonic() - started, skipped, unchanged=True)

    sid = new_snapshot_id()
    manifest = {
        "id": sid,
        "created": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "label": label,
        "trigger": trigger,
        "host": socket.gethostname(),
        "root": str(root),
        "secrets": include_secrets,
        "scope": sorted(scope_set) if scope_set is not None else None,
        "files": entries,
        "stats": {"files": len(entries), "bytes": total, "new_bytes": new_bytes, "new_blobs": new_blobs},
    }
    repo.save_manifest(manifest)
    return SnapshotResult(sid, len(entries), total, new_bytes, new_blobs, time.monotonic() - started, skipped)


BRAIN = ("memory", "soul", "skill", "config", "cron")


def _latest_covering(repo: Repository, scope_set) -> Optional[dict]:
    """Newest snapshot whose scope includes *scope_set* (a full snapshot covers everything)."""
    for sid in reversed(repo.snapshot_ids()):
        m = repo.load_manifest(sid)
        sc = m.get("scope")
        if sc is None or (scope_set is not None and scope_set <= set(sc)):
            return m
    return None


def _content_key(files: list[dict]) -> list[tuple]:
    return sorted((f["p"], tuple(f["b"])) for f in files)


@dataclass
class SnapshotInfo:
    id: str
    created: str
    label: str
    trigger: str
    files: int
    bytes: int
    new_bytes: int
    host: str
    scope: Optional[list] = None


def list_snapshots(repo: Repository) -> list[SnapshotInfo]:
    out = []
    for sid in repo.snapshot_ids():
        m = repo.load_manifest(sid)
        st = m.get("stats", {})
        out.append(SnapshotInfo(sid, m.get("created", ""), m.get("label", ""), m.get("trigger", ""),
                                st.get("files", len(m.get("files", []))), st.get("bytes", 0), st.get("new_bytes", 0), m.get("host", ""), m.get("scope")))
    return out


@dataclass
class Change:
    path: str
    status: str   # added | removed | modified
    kind: str
    item: str
    profile: str
    size_before: int
    size_after: int


def diff_manifests(old: dict, new: dict) -> list[Change]:
    a = {f["p"]: f for f in old["files"]}
    b = {f["p"]: f for f in new["files"]}
    changes = []
    for path in sorted(a.keys() | b.keys()):
        fa, fb = a.get(path), b.get(path)
        if fa and fb and fa["b"] == fb["b"]:
            continue
        status = "added" if not fa else "removed" if not fb else "modified"
        ref = fb or fa
        changes.append(Change(path, status, ref.get("k", "other"), ref.get("i", ""), ref.get("pr", "default"),
                              fa["s"] if fa else 0, fb["s"] if fb else 0))
    return changes


def human_size(n: float) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} GB"


def read_file_from_snapshot(repo: Repository, entry: dict) -> bytes:
    return b"".join(repo.get_blob(b) for b in entry["b"])

