"""Restore a snapshot — everything, or one piece of it (memory, a skill, a profile's config...).

Restore is planned first (``plan_restore``) and applied second (``apply_plan``), so every surface
can show the user exactly what will change before anything is touched. Within the selected scope
the disk is made to match the snapshot: changed files are rewritten, missing files recreated, and
files that did not exist at snapshot time are removed (the safety snapshot taken before applying
keeps them recoverable).
"""

from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Optional

from .repo import Repository
from .scanner import classify, scan

KINDS = ("memory", "soul", "skill", "config", "cron", "sessions", "plugin", "secrets", "media", "other")
_ALIASES = {"memories": "memory", "skills": "skill", "plugins": "plugin", "session": "sessions",
            "all": "", "everything": "", "*": ""}


class RestoreError(Exception):
    pass


@dataclass(frozen=True)
class Selector:
    """What to restore. Empty kind = everything; profile None = all profiles."""

    kind: str = ""
    item: str = ""
    profile: Optional[str] = None
    path: str = ""          # exact file or folder path (``file:...``)

    @classmethod
    def parse(cls, text: str, profile: Optional[str] = None) -> "Selector":
        """``all`` · ``memory`` · ``skills`` · ``skill:weather`` · ``plugin:x`` · ``file:skills/x/SKILL.md``
        · optionally prefixed by ``<profile>/`` (``work/memory``)."""
        text = (text or "all").strip()
        if text.startswith("file:"):
            p = PurePosixPath(text[5:].strip().strip("/"))
            if not p.parts or ".." in p.parts:
                raise RestoreError(f"invalid path {text[5:]!r}")
            return cls(path=p.as_posix(), profile=profile)
        if "/" in text and ":" not in text.split("/", 1)[0]:
            profile, text = text.split("/", 1)
        kind, _, item = text.partition(":")
        kind = _ALIASES.get(kind.lower(), kind.lower())
        if kind and kind not in KINDS:
            raise RestoreError(f"unknown item {kind!r}; use one of: all, {', '.join(KINDS)}, skill:<name>, file:<path>")
        return cls(kind=kind, item=item.strip(), profile=profile or None)

    def matches(self, rel: str) -> bool:
        if self.path:
            return rel == self.path or rel.startswith(self.path + "/")
        prof, kind, item = classify(rel)
        if self.profile and prof != self.profile:
            return False
        if self.kind and kind != self.kind:
            return False
        if self.item and item != self.item:
            return False
        return True

    def describe(self) -> str:
        if self.path:
            return f"file {self.path}"
        what = {"": "everything", "skill": "skills", "plugin": "plugins"}.get(self.kind, self.kind)
        if self.item:
            what = f"{self.kind} '{self.item}'"
        return what + (f" (profile {self.profile})" if self.profile else "")


@dataclass
class RestorePlan:
    snapshot_id: str
    selector: Selector
    write: list[dict] = field(default_factory=list)      # manifest entries to (re)write
    delete: list[str] = field(default_factory=list)      # rel paths to remove
    unchanged: int = 0

    @property
    def empty(self) -> bool:
        return not self.write and not self.delete

    @property
    def touches_sessions_db(self) -> bool:
        return any(PurePosixPath(e["p"]).name == "state.db" for e in self.write) or any(
            PurePosixPath(p).name == "state.db" for p in self.delete)


def _same_as_disk(repo: Repository, entry: dict, path: Path) -> bool:
    try:
        if not path.is_file() or path.is_symlink() or path.stat().st_size != entry["s"]:
            return False
        if path.name.endswith(".db"):
            return False  # live databases always differ byte-wise; restore explicitly
        data = path.read_bytes()
    except OSError:
        return False
    if len(entry["b"]) == 1:
        return repo.blob_id(data) == entry["b"][0]
    from .snapshot import CHUNK
    return [repo.blob_id(data[i:i + CHUNK]) for i in range(0, len(data), CHUNK)] == entry["b"]


def plan_restore(repo: Repository, snapshot_id: str, root: Path | str, selector: Selector,
                 extra_excludes=()) -> RestorePlan:
    root = Path(root)
    manifest = repo.load_manifest(snapshot_id)
    plan = RestorePlan(snapshot_id, selector)
    wanted = {e["p"]: e for e in manifest["files"] if selector.matches(e["p"])}
    for rel, entry in sorted(wanted.items()):
        if _same_as_disk(repo, entry, root / rel):
            plan.unchanged += 1
        else:
            plan.write.append(entry)
    # Files in scope today that the snapshot did not have. Secrets are only removed when the
    # snapshot itself captured secrets (otherwise their absence means "not backed up", not "deleted").
    for cur in scan(root, include_secrets=bool(manifest.get("secrets")), extra_excludes=extra_excludes):
        if cur.rel not in wanted and selector.matches(cur.rel):
            plan.delete.append(cur.rel)
    if not wanted and not plan.delete:
        raise RestoreError(f"snapshot {snapshot_id} has nothing matching {selector.describe()}")
    return plan


def _safe_target(root: Path, rel: str) -> Path:
    p = PurePosixPath(rel)
    if p.is_absolute() or ".." in p.parts or not p.parts:
        raise RestoreError(f"refusing unsafe path in snapshot: {rel!r}")
    target = root.joinpath(*p.parts)
    # Never follow a symlinked parent out of the home.
    real_root = root.resolve()
    parent = target.parent
    while parent != root and not parent.exists():
        parent = parent.parent
    if not str(parent.resolve()).startswith(str(real_root)):
        raise RestoreError(f"refusing to write outside the Hermes home: {rel!r}")
    return target


def _atomic_write(target: Path, data: bytes, mode: int, mtime_ns: Optional[int]) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_symlink():
        target.unlink()
    fd, tmp = tempfile.mkstemp(prefix=".ark-", dir=target.parent)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        os.chmod(tmp, mode or 0o600)
        if mtime_ns:
            os.utime(tmp, ns=(mtime_ns, mtime_ns))
        os.replace(tmp, target)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


@dataclass
class RestoreResult:
    written: int
    deleted: int
    bytes: int


def apply_plan(repo: Repository, plan: RestorePlan, root: Path | str) -> RestoreResult:
    """Apply *plan*. All blobs are fetched and verified BEFORE the first write, so a damaged or
    unreachable backup aborts cleanly instead of leaving a half-restored home."""
    root = Path(root)
    staged = []
    for entry in plan.write:
        data = b"".join(repo.get_blob(b) for b in entry["b"])
        if len(data) != entry["s"]:
            raise RestoreError(f"{entry['p']}: size mismatch in backup")
        staged.append((entry, _safe_target(root, entry["p"]), data))
    deletes = [_safe_target(root, rel) for rel in plan.delete]

    total = 0
    for entry, target, data in staged:
        if target.name.endswith(".db"):
            for suffix in ("-wal", "-shm", "-journal"):   # stale sidecars would corrupt the restored DB
                side = target.with_name(target.name + suffix)
                if side.exists():
                    side.unlink()
        _atomic_write(target, data, entry.get("m", 0o600), entry.get("t"))
        total += len(data)
    for target in deletes:
        try:
            target.unlink()
        except FileNotFoundError:
            pass
        _prune_empty_dirs(target.parent, root)
    return RestoreResult(len(staged), len(deletes), total)


def _prune_empty_dirs(d: Path, root: Path) -> None:
    root = root.resolve()
    d = d.resolve()
    while d != root and str(d).startswith(str(root)):
        try:
            d.rmdir()
        except OSError:
            return
        d = d.parent
