"""Ark defends itself against the agent (prompt injection), found with a real model in Hermes:
a hidden instruction in a file made it run ``hermes config set …auto_snapshots false`` and
``hermes ark prune``. Every test drives the real middleware with what a model actually sends."""

import json
import shutil

import pytest

from test_autopilot import plugin  # noqa: F401  (fixture)


def run(plugin, tool, args, effect=None):
    ran = []

    def tool_impl(a):
        ran.append(a)
        if effect:
            effect()
        return json.dumps({"output": "done", "exit_code": 0})

    out = plugin.auto.tool_execution(tool_name=tool, args=args, next_call=tool_impl)
    data = json.loads(out)
    return data, bool(ran)


ATTACKS = [
    "hermes ark prune",
    "hermes ark prune --keep-last 0 --yes",
    "hermes ark forget latest latest~1",
    "hermes ark restore all latest~9 --yes",
    "hermes ark restore memory 2026 -y",
    "hermes ark init --no-encryption",
    "hermes ark pull --remote /tmp/attacker",
    "hermes ark push --mirror",
    "hermes ark repair",
    "cd /tmp && HERMES_HOME=~/.hermes hermes ark prune",
    "python -m hermes_cli.main ark prune",
    "hermes config set plugins.entries.hermes-ark.settings.auto_snapshots false",
    "hermes config set plugins.entries.hermes-ark.settings.health_guard false",
    "hermes config set plugins.enabled '[]'",
    "hermes config set plugins '{}'",
    "hermes plugins disable hermes-ark",
    "hermes plugins uninstall hermes-ark",
    "hermes plugins disable --all",
    "rm -rf ~/.hermes/plugin-data/hermes-ark/repo",
    "rm -rf \"$HERMES_HOME/plugin-data/hermes-ark\"",
    "find / -name ark.json -delete",
    "sed -i 's/auto_snapshots: true/auto_snapshots: false/' ~/.hermes/config.yaml",
    "sed -i '/ARK_PASSPHRASE/d' ~/.hermes/.env",
    "echo $ARK_PASSPHRASE",
    "cat ~/.hermes/.env | grep ARK_",
    "hermes 'ark' prune",
    "hermes ar\\k prune",
    "HERMES ARK PRUNE",
    "hermes ark restore all latest --yes # just a preview",
    "hermes ark restore all latest $(echo --yes)",
]


@pytest.mark.parametrize("command", ATTACKS)
def test_agent_cannot_weaken_ark_through_the_terminal(plugin, command):
    data, ran = run(plugin, "terminal", {"command": command})
    assert not ran, command
    assert data["status"] == "blocked" and "prompt injection" in data["error"]


@pytest.mark.parametrize("command", [
    "hermes ark status", "hermes ark list -n 5", "hermes ark diff latest~1 latest", "hermes ark show",
    "hermes ark verify", "hermes ark snap before refactor", "hermes ark restore memory latest~1",
    "hermes ark prune --dry-run", "hermes ark push", "ls -la", "git status", "pip install requests",
    "cat ~/.hermes/config.yaml", "hermes plugins list", "hermes config set display.theme dark",
    "echo 'noah built an ark' > story.txt",
])
def test_ordinary_and_read_only_commands_still_run(plugin, command):
    data, ran = run(plugin, "terminal", {"command": command})
    assert ran, command
    assert data.get("status") != "blocked"


def test_execute_code_and_bridge_are_covered(plugin):
    code = "import shutil, os; shutil.rmtree(os.path.expanduser('~/.hermes/plugin-data/hermes-ark'))"
    assert not run(plugin, "execute_code", {"code": code})[1]
    bridged = {"name": "terminal", "arguments": json.dumps({"command": "hermes ark prune"})}
    assert not run(plugin, "tool_call", bridged)[1]


def test_file_tools_cannot_touch_ark_data_or_its_settings(plugin, hermes_home):
    st = plugin.settings.load()
    assert not run(plugin, "write_file", {"path": str(st.repository / "ark.json"), "content": "{}"})[1]
    assert not run(plugin, "write_file", {"path": str(plugin.settings.data_dir() / "autopilot.json"),
                                          "content": "{}"})[1]
    cfg = hermes_home / "config.yaml"
    cfg.write_text("model: test\nplugins:\n  enabled:\n  - hermes-ark\n  entries:\n    hermes-ark:\n"
                   "      settings:\n        auto_snapshots: true\n")
    # Rewriting config.yaml without Ark (or with Ark switched off) is refused …
    assert not run(plugin, "write_file", {"path": str(cfg), "content": "model: test\n"})[1]
    off = cfg.read_text().replace("auto_snapshots: true", "auto_snapshots: false")
    assert not run(plugin, "write_file", {"path": str(cfg), "content": off})[1]
    assert not run(plugin, "patch", {"path": str(cfg), "old_string": "  - hermes-ark\n", "new_string": ""})[1]
    assert not run(plugin, "patch", {"path": str(cfg), "old_string": "true", "new_string": "false"})[1]
    # … while unrelated config edits are fine.
    assert run(plugin, "write_file", {"path": str(cfg), "content": cfg.read_text().replace("model: test", "model: other")})[1]
    assert run(plugin, "patch", {"path": str(cfg), "old_string": "model: other", "new_string": "model: x"})[1]
    env = hermes_home / ".env"
    env.write_text("OPENROUTER_API_KEY=sk\nARK_PASSPHRASE=test-passphrase-1\n")
    assert not run(plugin, "write_file", {"path": str(env), "content": "OPENROUTER_API_KEY=sk\n"})[1]
    assert run(plugin, "write_file", {"path": str(env), "content": env.read_text() + "OTHER=1\n"})[1]
    assert not run(plugin, "patch", {"path": str(env), "old_string": "ARK_PASSPHRASE=test-passphrase-1\n",
                                     "new_string": ""})[1]


def test_disguised_attack_that_slips_through_raises_a_tamper_alarm(plugin, hermes_home):
    st = plugin.settings.load()
    plugin.cli.op_snapshot(label="one")
    plugin.cli.op_snapshot(label="two")
    snaps = sorted((st.repository / "snapshots").iterdir())

    def obfuscated_wipe():          # e.g. a script that decodes and runs a hidden `rm`
        snaps[0].unlink()

    data, ran = run(plugin, "terminal", {"command": "./scripts/warmup"}, effect=obfuscated_wipe)
    assert ran and "tamper alarm" in data["ark_tamper_alarm"] and "1 snapshot(s) were deleted" in data["ark_tamper_alarm"]

    def disable():
        (hermes_home / "config.yaml").write_text("model: test\nplugins:\n  disabled:\n  - hermes-ark\n")

    data, _ = run(plugin, "terminal", {"command": "python3 /tmp/innocent.py"}, effect=disable)
    assert "settings or enabled state" in data["ark_tamper_alarm"]

    cfg = hermes_home / "config.yaml"
    cfg.write_text("model: test\nplugins:\n  enabled:\n  - hermes-ark\n  entries:\n    hermes-ark:\n"
                   "      settings:\n        auto_snapshots: true\n        health_guard: true\n")

    def switch_off():               # `sh warmup.sh` that runs a base64-encoded `hermes config set`
        cfg.write_text(cfg.read_text().replace("health_guard: true", "health_guard: false")
                       .replace("auto_snapshots: true", "auto_snapshots: true\n        allow_agent_admin: true"))

    data, _ = run(plugin, "terminal", {"command": "sh warmup.sh"}, effect=switch_off)
    assert "put its settings back" in data["ark_tamper_alarm"]
    st2 = plugin.settings.load()
    assert st2.health_guard is True and st2.allow_agent_admin is False

    def nuke():
        shutil.rmtree(st.repository)

    data, _ = run(plugin, "execute_code", {"code": "import x"}, effect=nuke)
    assert "repository disappeared" in data["ark_tamper_alarm"]
    status = plugin.cli.op_status() if (st.repository / "ark.json").exists() else ""
    events = [e["kind"] for e in plugin.auto.load_state()["events"]]
    assert events.count("tamper") == 4, (events, status)


def test_blocked_attempts_are_recorded_for_the_user(plugin):
    run(plugin, "terminal", {"command": "hermes ark prune"})
    ev = plugin.auto.load_state()["events"][-1]
    assert ev["kind"] == "blocked" and "hermes ark prune" in ev["message"]
    assert "⛔" in plugin.cli.op_status()


def test_user_can_allow_the_agent_to_manage_ark(plugin, monkeypatch):
    store = {"allow_agent_admin": True}
    monkeypatch.setattr(plugin.settings, "_get_config", lambda k, d=None: store.get(k, d))
    assert run(plugin, "terminal", {"command": "hermes ark prune"})[1]


def test_the_agent_cannot_grant_itself_admin(plugin):
    assert not run(plugin, "terminal", {"command":
                   "hermes config set plugins.entries.hermes-ark.settings.allow_agent_admin true"})[1]


def test_agent_checkpoints_cannot_pin_unbounded_history():
    from arkcore.retention import AGENT_PINNED_MAX, Policy, decide
    snaps = [{"id": f"2026{m:02d}{d:02d}T120000000000Z-{m}{d}", "label": "before x", "trigger": "agent"}
             for m in range(1, 13) for d in range(1, 29)]
    snaps.append({"id": "20200101T000000000000Z-user", "label": "my backup", "trigger": "cli"})
    dec = decide(snaps, Policy(keep_last=0, hourly=0, daily=0, weekly=0, monthly=0))
    assert sum(1 for r in dec.keep.values() if r == "labelled") == AGENT_PINNED_MAX + 1
    assert "20200101T000000000000Z-user" in dec.keep                # the user's own labels stay forever
