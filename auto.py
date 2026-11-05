"""Autopilot: automatic snapshots, the health guard, and the agent's ``ark_checkpoint`` tool.

* Before every ``memory`` / ``skill_manage`` tool call, a *brain* snapshot (memory, SOUL, skills,
  config, cron) is taken — skipped when nothing changed, so it costs a directory walk.
* After that call, the health guard compares memory/skills before vs after. A sudden large loss is
  reported inside the tool result, so the agent sees it immediately and tells the user, together
  with the exact ``/ark restore`` command that undoes it.
* At the end of a session, if ``interval_minutes`` have passed, a full snapshot runs in a background
  thread; retention pruning runs at most once a day after it.

Nothing here may break the agent: every failure is logged and swallowed.
"""

from __future__ import annotations

import json
import logging
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

from . import settings as S
from .arkcore.guard import compare, format_alarms, measure
from .arkcore.lock import LockBusy, repo_lock
from .arkcore.repo import Repository
from .arkcore.retention import Policy, prune
from .arkcore.snapshot import BRAIN, create_snapshot

logger = logging.getLogger(__name__)

WATCHED_TOOLS = frozenset({"memory", "skill_manage"})
PRUNE_EVERY_S = 24 * 3600
_EVENTS_MAX = 200
_state_lock = threading.Lock()
_full_running = threading.Event()
_warned_unready = False


# ---- small persistent state (data dir) ---------------------------------------------------------

def _state_file() -> Path:
    return S.data_dir() / "autopilot.json"


def load_state() -> dict:
    try:
        return json.loads(_state_file().read_text())
    except (OSError, ValueError):
        return {}


def _update_state(**changes: Any) -> None:
    with _state_lock:
        st = load_state()
        st.update(changes)
        tmp = _state_file().with_suffix(".tmp")
        tmp.write_text(json.dumps(st, indent=1))
        tmp.replace(_state_file())


def record_event(kind: str, message: str, snapshot_id: Optional[str] = None) -> None:
    with _state_lock:
        st = load_state()
        events = st.get("events", [])
        events.append({"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "kind": kind,
                       "message": message, "snapshot": snapshot_id})
        st["events"] = events[-_EVENTS_MAX:]
        tmp = _state_file().with_suffix(".tmp")
        tmp.write_text(json.dumps(st, indent=1))
        tmp.replace(_state_file())


def record_push(objects: int, nbytes: int, where: str) -> None:
    _update_state(last_push={"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "objects": objects,
                             "bytes": nbytes, "where": where})


def record_verify(where: str, ok: bool, snapshots: int, damaged: int) -> None:
    _update_state(last_verify={"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "where": where,
                               "ok": ok, "snapshots": snapshots, "damaged": damaged})


def auto_push() -> None:
    """Push after an automatic snapshot when a remote is configured. Failures are recorded, not raised."""
    st = S.load()
    if not (st.remote and st.auto_push):
        return
    from .arkcore.sync import sync
    try:
        remote = S.remote_backend(st)
        res = sync(S.backend(st), remote)
        record_push(res.copied, res.bytes, remote.label)
    except Exception as exc:
        logger.warning("Hermes Ark: off-site push failed: %s", exc)
        record_event("error", f"off-site push failed: {exc}")


# ---- repository access ------------------------------------------------------------------------

def _ready_repo(st: S.Settings) -> Optional[Repository]:
    """The repo if Ark is set up and unlocked; None (logged once) otherwise."""
    global _warned_unready
    try:
        if not Repository.exists(S.backend(st)):
            return None
        return S.open_repo(st)
    except Exception as exc:
        if not _warned_unready:
            logger.warning("Hermes Ark: automatic snapshots paused (%s)", exc)
            _warned_unready = True
        return None


def brain_checkpoint(reason: str) -> Optional[str]:
    st = S.load()
    if not st.auto:
        return None
    repo = _ready_repo(st)
    if repo is None:
        return None
    with repo_lock(S.lock_path(st), timeout=10):
        res = create_snapshot(repo, S.hermes_home(), label=reason, trigger="auto:pre-write", scope=BRAIN,
                              include_secrets=False, extra_excludes=st.exclude, skip_if_unchanged=True)
    return res.snapshot_id


def full_snapshot(trigger: str = "auto", label: str = "", force_prune: bool = False) -> Optional[str]:
    st = S.load()
    repo = _ready_repo(st)
    if repo is None:
        return None
    with repo_lock(S.lock_path(st), timeout=60):
        res = create_snapshot(repo, S.hermes_home(), label=label, trigger=trigger,
                              include_secrets=st.include_secrets and repo.encrypted, extra_excludes=st.exclude,
                              skip_if_unchanged=True)
        now = time.time()
        _update_state(last_full=now, last_full_id=res.snapshot_id)
        if force_prune or now - load_state().get("last_prune", 0) >= PRUNE_EVERY_S:
            pr = prune(repo, Policy.from_settings(S.get))
            _update_state(last_prune=now)
            if pr.removed_snapshots:
                logger.info("Hermes Ark: pruned %d snapshot(s), %d blob(s)", len(pr.removed_snapshots), pr.removed_blobs)
    return res.snapshot_id


# ---- hooks / middleware -----------------------------------------------------------------------

def on_session_end(**_: Any) -> None:
    """Full snapshot in the background when the interval has elapsed. Never blocks the agent."""
    try:
        st = S.load()
        if not st.auto or _full_running.is_set():
            return
        if time.time() - load_state().get("last_full", 0) < st.interval_minutes * 60:
            return
    except Exception:
        return
    _full_running.set()

    def run():
        try:
            if full_snapshot() is not None:
                auto_push()      # outside the lock: blobs are immutable, uploads can be slow
        except LockBusy:
            pass
        except Exception as exc:
            logger.warning("Hermes Ark: automatic snapshot failed: %s", exc)
            try:
                record_event("error", f"automatic snapshot failed: {exc}")
            except Exception:
                pass
        finally:
            _full_running.clear()

    threading.Thread(target=run, name="hermes-ark-snapshot", daemon=True).start()


def _attach_note(result: Any, note: str) -> Any:
    """Add the guard's note to a tool result without breaking its JSON shape."""
    if isinstance(result, str):
        try:
            data = json.loads(result)
        except ValueError:
            return result + "\n\n" + note
        if isinstance(data, dict):
            data["ark_health_guard"] = note
            return json.dumps(data, ensure_ascii=False)
        return result + "\n\n" + note
    if isinstance(result, dict):
        return {**result, "ark_health_guard": note}
    return result


def tool_execution(tool_name: str = "", args: Any = None, next_call=None, **_: Any) -> Any:
    """Middleware around memory/skill writes: checkpoint before, guard after."""
    if tool_name not in WATCHED_TOOLS:
        return next_call(args)
    snapshot_id = before = None
    guard_on = False
    try:
        st = S.load()
        guard_on = st.health_guard
        action = (args or {}).get("action", "") if isinstance(args, dict) else ""
        if guard_on:
            before = measure(S.hermes_home())
        snapshot_id = brain_checkpoint(f"before {tool_name} {action}".strip())
    except Exception as exc:  # never block the tool
        logger.debug("Hermes Ark pre-write checkpoint skipped: %s", exc)
    result = next_call(args)
    if before is None:
        return result
    try:
        alarms = compare(before, measure(S.hermes_home()))
        if alarms:
            note = format_alarms(alarms, snapshot_id)
            for a in alarms:
                record_event("guard", a.message, snapshot_id)
            logger.warning("Hermes Ark health guard: %s", "; ".join(a.message for a in alarms))
            return _attach_note(result, note)
    except Exception as exc:
        logger.debug("Hermes Ark guard check failed: %s", exc)
    return result


# ---- agent tool ------------------------------------------------------------------------------

CHECKPOINT_SCHEMA = {
    "name": "ark_checkpoint",
    "description": (
        "Save a restorable snapshot of Hermes itself (memory, SOUL, skills, config, cron, sessions, all "
        "profiles) before you do something risky to them: bulk-editing or deleting memory or skills, "
        "changing config, reorganising cron jobs, or when the user asks for a backup. Returns the snapshot "
        "id; the user can restore with /ark restore <what> <id>. Does not back up project files."),
    "parameters": {
        "type": "object",
        "properties": {
            "label": {"type": "string", "description": "Short reason, e.g. 'before merging duplicate skills'"},
        },
        "required": ["label"],
    },
}


def checkpoint_available() -> bool:
    try:
        st = S.load()
        return Repository.exists(S.backend(st))
    except Exception:
        return False


def checkpoint_tool(args: dict, **_: Any) -> str:
    label = str((args or {}).get("label", "")).strip()[:120] or "agent checkpoint"
    try:
        sid = full_snapshot(trigger="agent", label=label)
        if sid is None:
            return json.dumps({"ok": False, "error": "Hermes Ark is not set up or the repository is locked "
                                                     "(the user must run `hermes ark init` and set ARK_PASSPHRASE)."})
        return json.dumps({"ok": True, "snapshot_id": sid, "label": label,
                           "restore_hint": f"/ark restore <all|memory|skills|skill:NAME|config|cron> {sid}"})
    except LockBusy:
        return json.dumps({"ok": False, "error": "another Ark backup is running; try again in a minute"})
    except Exception as exc:
        return json.dumps({"ok": False, "error": f"{exc.__class__.__name__}: {exc}"})
