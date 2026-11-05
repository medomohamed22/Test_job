"""Smart retention ("keep hourly for a day, daily for a week...") and garbage collection."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Iterable, Optional

from .repo import Repository

# Manual snapshots the user named are kept forever unless deleted explicitly.
PINNED_TRIGGERS = ("cli", "chat", "manual", "desktop")
# Labelled snapshots the agent took (ark_checkpoint) are kept too, but only the newest few: a looping or
# prompt-injected agent must not be able to fill the disk with snapshots retention can never remove.
AGENT_PINNED_MAX = 20


@dataclass
class Policy:
    keep_last: int = 10
    hourly: int = 24
    daily: int = 7
    weekly: int = 4
    monthly: int = 12
    keep_labelled: bool = True

    @classmethod
    def from_settings(cls, get) -> "Policy":
        def num(key, default):
            try:
                return max(0, int(get(key, default)))
            except (TypeError, ValueError):
                return default
        d = cls()
        return cls(num("keep_last", d.keep_last), num("keep_hourly", d.hourly), num("keep_daily", d.daily),
                   num("keep_weekly", d.weekly), num("keep_monthly", d.monthly))


@dataclass
class Decision:
    keep: dict[str, str] = field(default_factory=dict)   # id -> reason
    drop: list[str] = field(default_factory=list)


def _when(snapshot_id: str) -> datetime:
    return datetime.strptime(snapshot_id[:15], "%Y%m%dT%H%M%S").replace(tzinfo=timezone.utc)


def decide(snapshots: Iterable[dict], policy: Policy) -> Decision:
    """*snapshots*: dicts with ``id``, ``label``, ``trigger``. Newest wins inside each time bucket."""
    snaps = sorted(snapshots, key=lambda s: s["id"], reverse=True)
    out = Decision()
    for s in snaps[:policy.keep_last]:
        out.keep.setdefault(s["id"], "last")
    buckets = (
        ("hourly", policy.hourly, lambda t: t.strftime("%Y%m%d%H")),
        ("daily", policy.daily, lambda t: t.strftime("%Y%m%d")),
        ("weekly", policy.weekly, lambda t: "%d-%02d" % t.isocalendar()[:2]),
        ("monthly", policy.monthly, lambda t: t.strftime("%Y%m")),
    )
    for name, count, key in buckets:
        seen: list[str] = []
        for s in snaps:
            if len(seen) >= count:
                break
            k = key(_when(s["id"]))
            if k not in seen:
                seen.append(k)
                out.keep.setdefault(s["id"], name)
    agent_kept = 0
    for s in snaps:
        if not (policy.keep_labelled and s.get("label")):
            continue
        if s.get("trigger") in PINNED_TRIGGERS:
            out.keep.setdefault(s["id"], "labelled")
        elif s.get("trigger") == "agent" and agent_kept < AGENT_PINNED_MAX:
            agent_kept += 1
            out.keep.setdefault(s["id"], "labelled")
    out.drop = [s["id"] for s in snaps if s["id"] not in out.keep]
    return out


@dataclass
class PruneResult:
    kept: int
    removed_snapshots: list[str]
    removed_blobs: int
    freed_bytes: int


def prune(repo: Repository, policy: Policy, dry_run: bool = False, only: Optional[list[str]] = None,
          protect: Iterable[str] = ()) -> PruneResult:
    """Apply *policy* (or delete exactly *only*), then delete blobs no remaining snapshot uses.

    Call under the repository lock: a blob written by a snapshot still in progress is not yet
    referenced by any manifest and would otherwise be collected.
    """
    manifests = {sid: repo.load_manifest(sid) for sid in repo.snapshot_ids()}
    if only is not None:
        drop = [repo.resolve(r) for r in only]
    else:
        protected = set(protect)
        drop = [sid for sid in decide(({"id": sid, "label": m.get("label", ""), "trigger": m.get("trigger", "")}
                                       for sid, m in manifests.items()), policy).drop if sid not in protected]
    keep = {sid: m for sid, m in manifests.items() if sid not in drop}
    referenced = {b for m in keep.values() for f in m["files"] for b in f["b"]}
    garbage = [b for b in repo.iter_blob_ids() if b not in referenced]
    freed = 0
    if not dry_run:
        for sid in drop:
            repo.delete_manifest(sid)
        for b in garbage:
            freed += _size(repo, b)
            repo.delete_blob(b)
    else:
        freed = sum(_size(repo, b) for b in garbage)
    return PruneResult(len(keep), drop, len(garbage), freed)


def _size(repo: Repository, blob_id: str) -> int:
    path = getattr(repo.backend, "root", None)
    if path is None:
        return 0
    try:
        return (path / "blobs" / blob_id[:2] / blob_id).stat().st_size
    except OSError:
        return 0
