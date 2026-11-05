import importlib.util
import json
import sys
from datetime import datetime, timedelta, timezone

import pytest

from arkcore.backends import LocalBackend
from arkcore.guard import compare, measure
from arkcore.repo import Repository
from arkcore.restore import Selector, apply_plan, plan_restore
from arkcore.retention import Policy, decide, prune
from arkcore.snapshot import BRAIN, create_snapshot, list_snapshots
from conftest import ROOT

# ---- retention ---------------------------------------------------------------------------------


def _sid(dt):
    return dt.strftime("%Y%m%dT%H%M%S") + "000000Z-abcd"


def test_retention_buckets():
    now = datetime(2026, 11, 5, 12, tzinfo=timezone.utc)
    snaps = [{"id": _sid(now - timedelta(hours=h)), "label": "", "trigger": "auto"} for h in range(0, 24 * 60, 1)]
    d = decide(snaps, Policy(keep_last=5, hourly=24, daily=7, weekly=4, monthly=3))
    kept = set(d.keep)
    assert len(kept) < len(snaps)
    assert {s["id"] for s in snaps[:24]} <= kept                       # every hour of the last day
    assert len(kept) <= 5 + 24 + 7 + 4 + 3
    # Monthly buckets keep the newest snapshot of each month (Sep 30 23:00 here) — older ones go.
    assert _sid(datetime(2026, 9, 30, 23, tzinfo=timezone.utc)) in kept
    assert snaps[-1]["id"] not in kept


def test_retention_keeps_labelled_manual_snapshots():
    base = datetime(2025, 1, 1, tzinfo=timezone.utc)
    snaps = [{"id": _sid(base + timedelta(days=i)), "label": "", "trigger": "auto"} for i in range(400)]
    snaps.append({"id": _sid(base - timedelta(days=30)), "label": "before migration", "trigger": "cli"})
    d = decide(snaps, Policy(keep_last=1, hourly=0, daily=0, weekly=0, monthly=0))
    assert d.keep[_sid(base - timedelta(days=30))] == "labelled"
    assert len(d.keep) == 2


def test_prune_removes_unreferenced_blobs_only(hermes_home, tmp_path):
    repo = Repository.init(LocalBackend(tmp_path / "repo"), None)
    first = create_snapshot(repo, hermes_home).snapshot_id
    (hermes_home / "memories" / "MEMORY.md").write_text("x" * 5000)
    second = create_snapshot(repo, hermes_home).snapshot_id
    res = prune(repo, Policy(), only=[first])
    assert res.removed_snapshots == [first] and res.removed_blobs >= 1
    # The remaining snapshot is still fully restorable.
    (hermes_home / "SOUL.md").write_text("broken")
    apply_plan(repo, plan_restore(repo, second, hermes_home, Selector()), hermes_home)
    assert (hermes_home / "SOUL.md").read_text().startswith("You are")


# ---- scoped (brain) snapshots -----------------------------------------------------------------

def test_brain_snapshot_scope_and_skip(hermes_home, tmp_path):
    repo = Repository.init(LocalBackend(tmp_path / "repo"), None)
    res = create_snapshot(repo, hermes_home, scope=BRAIN)
    kinds = {f["k"] for f in repo.load_manifest(res.snapshot_id)["files"]}
    assert kinds <= set(BRAIN) and "sessions" not in kinds and "media" not in kinds
    assert create_snapshot(repo, hermes_home, scope=BRAIN, skip_if_unchanged=True).unchanged
    # A session-only change does not create a new brain snapshot.
    (hermes_home / "sessions").mkdir(exist_ok=True)
    (hermes_home / "sessions" / "s1.json").write_text("{}")
    assert create_snapshot(repo, hermes_home, scope=BRAIN, skip_if_unchanged=True).unchanged
    assert list_snapshots(repo)[-1].scope == sorted(BRAIN)


def test_restore_all_from_brain_snapshot_leaves_other_kinds(hermes_home, tmp_path):
    repo = Repository.init(LocalBackend(tmp_path / "repo"), None)
    sid = create_snapshot(repo, hermes_home, scope=BRAIN).snapshot_id
    (hermes_home / "cache" / "images" / "dog.png").write_bytes(b"new image")       # media, out of scope
    (hermes_home / "memories" / "MEMORY.md").write_text("")
    plan = plan_restore(repo, sid, hermes_home, Selector())
    assert "cache/images/dog.png" not in plan.delete
    apply_plan(repo, plan, hermes_home)
    assert (hermes_home / "cache" / "images" / "dog.png").exists()
    assert "Hermes Ark" in (hermes_home / "memories" / "MEMORY.md").read_text()


# ---- guard -----------------------------------------------------------------------------------

def test_guard_flags_large_memory_loss_only(hermes_home):
    (hermes_home / "memories" / "MEMORY.md").write_text("- fact\n" * 200)
    before = measure(hermes_home)
    (hermes_home / "memories" / "MEMORY.md").write_text("- fact\n" * 195)              # normal trim
    assert compare(before, measure(hermes_home)) == []
    (hermes_home / "memories" / "MEMORY.md").write_text("- fact\n")                    # wiped
    alarms = compare(before, measure(hermes_home))
    assert len(alarms) == 1 and alarms[0].what == "memory" and "shrank" in alarms[0].message


def test_guard_flags_mass_skill_removal(hermes_home):
    import shutil
    for n in ("a", "b", "c"):
        (hermes_home / "skills" / n).mkdir()
        (hermes_home / "skills" / n / "SKILL.md").write_text("# " + n)
    before = measure(hermes_home)
    shutil.rmtree(hermes_home / "skills" / "a")
    assert compare(before, measure(hermes_home)) == []                                 # one of four: fine
    shutil.rmtree(hermes_home / "skills" / "b")
    shutil.rmtree(hermes_home / "skills" / "c")
    alarms = compare(before, measure(hermes_home))
    assert alarms and alarms[0].what == "skills"


# ---- plugin layer inside a real Hermes runtime -------------------------------------------------

hermes_available = importlib.util.find_spec("hermes_constants") is not None


@pytest.fixture
def plugin(hermes_home, monkeypatch):
    if not hermes_available:
        pytest.skip("Hermes is not installed in this environment")
    monkeypatch.setenv("HERMES_HOME", str(hermes_home))
    monkeypatch.setenv("ARK_PASSPHRASE", "test-passphrase-1")
    name = "hermes_ark_under_test"
    for mod in [m for m in sys.modules if m == name or m.startswith(name + ".")]:
        del sys.modules[mod]
    spec = importlib.util.spec_from_file_location(name, ROOT / "__init__.py", submodule_search_locations=[str(ROOT)])
    pkg = importlib.util.module_from_spec(spec)
    sys.modules[name] = pkg
    spec.loader.exec_module(pkg)
    pkg.settings._cache.clear()
    pkg.cli.op_init(no_encryption=False)
    return pkg


def test_middleware_checkpoints_and_guards_memory_wipe(plugin, hermes_home):
    (hermes_home / "memories" / "MEMORY.md").write_text("- important fact about the user\n" * 20)

    def fake_memory_tool(args):            # what the real memory tool would do on a bad "cleanup"
        (hermes_home / "memories" / "MEMORY.md").write_text("")
        return json.dumps({"success": True})

    out = plugin.auto.tool_execution(tool_name="memory", args={"action": "remove"}, next_call=fake_memory_tool)
    data = json.loads(out)
    assert data["success"] is True and "health guard" in data["ark_health_guard"]
    sid = data["ark_health_guard"].split("snapshot ")[1].split(".")[0]
    # The command in the warning really undoes the damage.
    msg = plugin.cli.handle_slash(f"restore memory {sid} yes")
    assert "Restored 1 file" in msg
    assert "important fact" in (hermes_home / "memories" / "MEMORY.md").read_text()
    assert "⚠" in plugin.cli.op_status()


def test_middleware_passes_other_tools_and_survives_errors(plugin, monkeypatch):
    calls = []
    assert plugin.auto.tool_execution(tool_name="terminal", args={"command": "ls"}, next_call=lambda a: calls.append(a) or "ok") == "ok"
    monkeypatch.setattr(plugin.auto, "brain_checkpoint", lambda reason: (_ for _ in ()).throw(RuntimeError("disk full")))
    assert plugin.auto.tool_execution(tool_name="memory", args={}, next_call=lambda a: "done") == "done"
    assert len(calls) == 1


def test_checkpoint_tool_and_prune(plugin):
    out = json.loads(plugin.auto.checkpoint_tool({"label": "before merging skills"}))
    assert out["ok"] and out["snapshot_id"]
    assert "Removed" in plugin.cli.op_prune(dry_run=False) or "Kept" in plugin.cli.op_prune()


def test_on_session_end_runs_full_snapshot_in_background(plugin):
    plugin.auto.on_session_end()
    for _ in range(100):
        if not plugin.auto._full_running.is_set() and plugin.auto.load_state().get("last_full"):
            break
        import time
        time.sleep(0.05)
    assert plugin.auto.load_state().get("last_full_id")
    plugin.auto.on_session_end()   # within the interval: no second run
    assert not plugin.auto._full_running.is_set()


def test_push_pull_verify_and_restore_keeps_offsite_settings(plugin, hermes_home, tmp_path, monkeypatch):
    store = {}
    monkeypatch.setattr(plugin.settings, "_get_config", lambda k, d=None: store.get(k, d))
    monkeypatch.setattr(plugin.settings, "_set_config", lambda k, v: store.__setitem__(k, v))
    plugin.cli.op_snapshot(label="no remote yet")
    old = plugin.settings.open_repo().snapshot_ids()[-1]
    usb = tmp_path / "usb-drive"
    store["remote"] = str(usb)
    assert "Pushed" in plugin.cli.op_push()
    assert "up to date" in plugin.cli.op_push()
    assert "Verified" in plugin.cli.op_verify(remote=True)
    # Restoring an old config must not silently disable this machine's off-site copy.
    store.pop("remote")                       # simulate the restored config.yaml lacking it...
    st_before = plugin.settings.Settings(**{**plugin.settings.load().__dict__, "remote": str(usb)})
    assert plugin.cli._keep_offsite_settings(st_before) is True
    assert store["remote"] == str(usb)
    # `latest` skips pre-restore safety copies.
    (hermes_home / "memories" / "MEMORY.md").write_text("")
    plugin.cli.op_restore("memory", old, yes=True)
    repo = plugin.settings.open_repo()
    sid, note = plugin.cli._restore_target(repo, "latest")
    assert sid != repo.snapshot_ids()[-1] and "safety" in note


def test_guard_also_catches_direct_file_edits(plugin, hermes_home):
    (hermes_home / "memories" / "MEMORY.md").write_text("- important fact\n" * 30)
    mem = str(hermes_home / "memories" / "MEMORY.md")

    def shell_wipe(args):                       # what `echo "" > MEMORY.md` via the terminal tool does
        (hermes_home / "memories" / "MEMORY.md").write_text("")
        return json.dumps({"output": "", "exit_code": 0})

    out = json.loads(plugin.auto.tool_execution(tool_name="terminal", args={"command": f'echo "" > {mem}'}, next_call=shell_wipe))
    assert "health guard" in out["ark_health_guard"]
    assert plugin.auto._touches_brain("write_file", {"path": str(hermes_home / "skills" / "weather" / "SKILL.md")})
    assert plugin.auto._touches_brain("patch", {"path": mem})
    assert not plugin.auto._touches_brain("write_file", {"path": "/tmp/project/main.py"})
    assert not plugin.auto._touches_brain("terminal", {"command": "pytest -q"})
    assert not plugin.auto._touches_brain("write_file", {"path": str(hermes_home / "logs" / "x.log")})


def test_guard_catches_skills_deleted_one_call_at_a_time(plugin, hermes_home):
    import shutil
    for n in ("a", "b", "c", "d", "e", "f"):
        (hermes_home / "skills" / n).mkdir()
        (hermes_home / "skills" / n / "SKILL.md").write_text("# " + n)

    def delete(name):
        def run(args):
            shutil.rmtree(hermes_home / "skills" / name)
            return json.dumps({"success": True})
        return run

    notes = []
    for n in ("a", "b", "c"):                       # three separate tool calls in one conversation
        out = json.loads(plugin.auto.tool_execution(tool_name="skill_manage", args={"action": "delete", "name": n},
                                                    next_call=delete(n), session_id="s1"))
        notes.append(out.get("ark_health_guard"))
    assert notes[0] is None                         # one skill: normal
    assert notes[1] and "skill(s) removed" in notes[1]   # second deletion: cumulative alarm
    sid = notes[1].split("snapshot ")[1].split(".")[0]
    msg = plugin.cli.handle_slash(f"restore skills {sid} yes")
    assert "Restored" in msg and (hermes_home / "skills" / "a" / "SKILL.md").exists()
    # A different conversation starts from its own baseline.
    out = json.loads(plugin.auto.tool_execution(tool_name="skill_manage", args={"action": "delete", "name": "d"},
                                                next_call=delete("d"), session_id="s2"))
    assert out.get("ark_health_guard") is None
