"""Copy a repository between backends (push off-site, pull onto a new machine) and verify it.

Objects are immutable (blobs are content-addressed, manifests are written once), so syncing is
"copy what the other side does not have". It is additive by default; ``mirror=True`` also deletes
objects the source no longer has (after pruning), but only between two copies of the **same**
repository and never from a source with zero snapshots — an empty or wrong local repository can
never wipe the off-site copy.
"""

from __future__ import annotations

import json
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Callable, Optional

from .backends import Backend, BackendError
from .repo import CONFIG_KEY, Repository


class SyncError(Exception):
    pass


@dataclass
class SyncResult:
    copied: int = 0
    bytes: int = 0
    deleted: int = 0
    skipped: int = 0
    snapshots_copied: list[str] = field(default_factory=list)


def _repo_id(backend: Backend) -> Optional[str]:
    try:
        return json.loads(backend.read(CONFIG_KEY)).get("id")
    except (BackendError, ValueError):
        return None


def sync(src: Backend, dst: Backend, *, mirror: bool = False, workers: int = 8,
         progress: Optional[Callable[[str], None]] = None) -> SyncResult:
    src_id = _repo_id(src)
    if not src_id:
        raise SyncError(f"no Ark repository at {src.label}")
    dst_id = _repo_id(dst)
    if dst_id and dst_id != src_id:
        raise SyncError(f"{dst.label} holds a different Ark repository ({dst_id}); refusing to mix them")

    src_keys = set(src.list("blobs")) | set(src.list("snapshots"))
    dst_keys = set(dst.list("blobs")) | set(dst.list("snapshots"))
    # Blobs first, manifests last: a manifest only appears once everything it references is there,
    # so an interrupted push never leaves a snapshot that cannot be restored.
    todo = sorted(k for k in src_keys - dst_keys if k.startswith("blobs/")) + \
        sorted(k for k in src_keys - dst_keys if k.startswith("snapshots/"))
    res = SyncResult(skipped=len(src_keys & dst_keys))

    def copy(key: str) -> int:
        data = src.read(key)
        dst.write(key, data)
        if progress:
            progress(key)
        return len(data)

    blobs = [k for k in todo if k.startswith("blobs/")]
    manifests = [k for k in todo if k.startswith("snapshots/")]
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        res.bytes += sum(pool.map(copy, blobs))
    for k in manifests:
        res.bytes += copy(k)
        res.snapshots_copied.append(k.rsplit("/", 1)[-1])
    res.copied = len(todo)

    # The config is copied when missing or changed (passphrase rotation re-wraps the key).
    cfg = src.read(CONFIG_KEY)
    try:
        same = dst.read(CONFIG_KEY) == cfg
    except BackendError:
        same = False
    if not same:
        dst.write(CONFIG_KEY, cfg)

    if mirror:
        if not any(k.startswith("snapshots/") for k in src_keys):
            raise SyncError("refusing to mirror from a repository with no snapshots")
        stale = sorted(dst_keys - src_keys)
        # Manifests first, so the destination never lists a snapshot whose blobs are gone.
        for k in [k for k in stale if k.startswith("snapshots/")] + [k for k in stale if k.startswith("blobs/")]:
            dst.delete(k)
            res.deleted += 1
    return res


@dataclass
class VerifyReport:
    snapshots: int = 0
    blobs_checked: int = 0
    bytes_checked: int = 0
    missing: dict[str, list[str]] = field(default_factory=dict)    # blob -> snapshots that need it
    corrupt: dict[str, list[str]] = field(default_factory=dict)
    unreadable_snapshots: list[str] = field(default_factory=list)
    unreferenced: int = 0

    @property
    def ok(self) -> bool:
        return not (self.missing or self.corrupt or self.unreadable_snapshots)

    def damaged_snapshots(self) -> list[str]:
        bad = {s for v in list(self.missing.values()) + list(self.corrupt.values()) for s in v}
        return sorted(bad | set(self.unreadable_snapshots))


def verify(repo: Repository, *, read_data: bool = True, workers: int = 8,
           progress: Optional[Callable[[str], None]] = None) -> VerifyReport:
    """Prove the repository can be restored.

    ``read_data=True`` downloads, decrypts and re-hashes every blob that any snapshot needs
    (exactly what a restore would do); ``False`` only checks that each blob exists.
    """
    rep = VerifyReport()
    needed: dict[str, list[str]] = {}
    for sid in repo.snapshot_ids():
        rep.snapshots += 1
        try:
            m = repo.load_manifest(sid)
        except Exception:
            rep.unreadable_snapshots.append(sid)
            continue
        for f in m["files"]:
            for b in f["b"]:
                needed.setdefault(b, []).append(sid)
    present = set(repo.iter_blob_ids())
    rep.unreferenced = len(present - needed.keys())
    for b, sids in needed.items():
        if b not in present:
            rep.missing[b] = sorted(set(sids))

    def check(b: str) -> tuple[str, int, bool]:
        try:
            data = repo.get_blob(b)
            return b, len(data), True
        except Exception:
            return b, 0, False

    todo = [b for b in needed if b in present]
    if read_data:
        with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
            for b, n, good in pool.map(check, todo):
                rep.blobs_checked += 1
                rep.bytes_checked += n
                if not good:
                    rep.corrupt[b] = sorted(set(needed[b]))
                if progress:
                    progress(b)
    else:
        rep.blobs_checked = len(todo)
    return rep

