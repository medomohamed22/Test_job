"""``hermes ark ...`` (terminal) and ``/ark ...`` (any chat surface) commands."""

from __future__ import annotations

import argparse
import getpass
import os
import sys
from typing import Optional

from . import settings as S
from .arkcore.repo import LockedError, Repository, RepoError
from .arkcore.snapshot import create_snapshot, diff_manifests, human_size, list_snapshots

KIND_ICONS = {"memory": "🧠", "soul": "✨", "skill": "🛠", "config": "⚙", "cron": "⏰", "sessions": "💬",
              "plugin": "🔌", "secrets": "🔑", "media": "🖼", "other": "📄"}


# ---------------------------------------------------------------------------------------------
# Shared operations (return text; never raise for expected user errors)
# ---------------------------------------------------------------------------------------------

def op_status() -> str:
    st = S.load()
    be = S.backend(st)
    if not Repository.exists(be):
        return ("Ark is not set up yet.\n"
                "  Run `hermes ark init` in a terminal to create an encrypted repository.\n"
                f"  Repository location: {st.repository}")
    try:
        repo = S.open_repo(st)
    except LockedError as exc:
        return f"🔒 {exc}. Set ARK_PASSPHRASE (hermes config set secret or .env) and try again."
    snaps = list_snapshots(repo)
    lines = [f"🛡  Ark repository: {st.repository}",
             f"   encryption: {'AES-256-GCM 🔒' if repo.encrypted else 'off ⚠'} · credentials: {'included' if st.include_secrets else 'excluded'}",
             f"   snapshots: {len(snaps)}"]
    if snaps:
        last = snaps[-1]
        lines.append(f"   latest: {last.id}  {last.created}  {last.files} files, {human_size(last.bytes)}"
                     + (f"  [{last.label}]" if last.label else ""))
    return "\n".join(lines)


def op_snapshot(label: str = "", trigger: str = "manual", skip_if_unchanged: bool = False) -> str:
    st = S.load()
    repo = S.open_repo(st)
    include = st.include_secrets and repo.encrypted
    res = create_snapshot(repo, S.hermes_home(), label=label, trigger=trigger, include_secrets=include,
                          extra_excludes=st.exclude, skip_if_unchanged=skip_if_unchanged)
    if res.unchanged:
        return f"✓ Nothing changed since {res.snapshot_id}; no new snapshot needed."
    msg = (f"✓ Snapshot {res.snapshot_id}: {res.files} files, {human_size(res.bytes_total)} "
           f"(new data {human_size(res.bytes_new)}) in {res.seconds:.1f}s")
    if st.include_secrets and not repo.encrypted:
        msg += "\n⚠ Credentials were NOT included: the repository is not encrypted."
    if res.skipped:
        msg += f"\n⚠ {len(res.skipped)} file(s) could not be read: " + "; ".join(res.skipped[:5])
    return msg


def op_list(limit: int = 20) -> str:
    repo = S.open_repo()
    snaps = list_snapshots(repo)
    if not snaps:
        return "No snapshots yet. Create one with `/ark snap` or `hermes ark snap`."
    lines = [f"{'#':>3}  {'snapshot':<26} {'when (UTC)':<20} {'files':>6} {'size':>9}  trigger / label"]
    for n, s in enumerate(reversed(snaps[-limit:])):
        lines.append(f"{n:>3}  {s.id:<26} {s.created[:19].replace('T', ' '):<20} {s.files:>6} {human_size(s.bytes):>9}  "
                     f"{s.trigger}{' · ' + s.label if s.label else ''}")
    if len(snaps) > limit:
        lines.append(f"… {len(snaps) - limit} older snapshot(s) not shown")
    lines.append("(#0 = latest; refer to one as latest~N or by id prefix)")
    return "\n".join(lines)


def op_diff(old_ref: str = "latest~1", new_ref: str = "latest") -> str:
    repo = S.open_repo()
    old_id, new_id = repo.resolve(old_ref), repo.resolve(new_ref)
    changes = diff_manifests(repo.load_manifest(old_id), repo.load_manifest(new_id))
    if not changes:
        return f"No differences between {old_id} and {new_id}."
    lines = [f"Changes {old_id} → {new_id}:"]
    sign = {"added": "+", "removed": "-", "modified": "~"}
    for c in changes[:60]:
        delta = f"{human_size(c.size_before)} → {human_size(c.size_after)}" if c.status == "modified" else human_size(c.size_after or c.size_before)
        lines.append(f"  {sign[c.status]} {KIND_ICONS.get(c.kind, '📄')} {c.path}  ({delta})")
    if len(changes) > 60:
        lines.append(f"  … and {len(changes) - 60} more")
    return "\n".join(lines)


def _guard(fn, *a, **kw) -> str:
    try:
        return fn(*a, **kw)
    except LockedError as exc:
        return f"🔒 {exc}."
    except (RepoError, ValueError, OSError) as exc:
        return f"✗ {exc}"


# ---------------------------------------------------------------------------------------------
# /ark slash command
# ---------------------------------------------------------------------------------------------

SLASH_HELP = """/ark — encrypted, versioned backups of your Hermes

  /ark                 status of your backups
  /ark snap [label]    take a snapshot now
  /ark list            list snapshots
  /ark diff [A] [B]    what changed between two snapshots (default: previous → latest)

Set up once in a terminal: hermes ark init"""


def handle_slash(raw_args: str = "", **_: object) -> str:
    argv = (raw_args or "").strip().split()
    if not argv or argv[0] == "status":
        return _guard(op_status)
    cmd, rest = argv[0], argv[1:]
    if cmd in ("help", "-h", "--help"):
        return SLASH_HELP
    if cmd in ("snap", "snapshot", "backup"):
        return _guard(op_snapshot, label=" ".join(rest), trigger="chat")
    if cmd in ("list", "ls", "log"):
        return _guard(op_list)
    if cmd == "diff":
        return _guard(op_diff, *(rest[:2] or []))
    return f"Unknown subcommand {cmd!r}.\n\n{SLASH_HELP}"


# ---------------------------------------------------------------------------------------------
# hermes ark (argparse)
# ---------------------------------------------------------------------------------------------

def setup_parser(parser: argparse.ArgumentParser) -> None:
    sub = parser.add_subparsers(dest="ark_command")
    p = sub.add_parser("init", help="Create the backup repository")
    p.add_argument("--no-encryption", action="store_true", help="Create an unencrypted repository (not recommended)")
    sub.add_parser("status", help="Show repository status")
    p = sub.add_parser("snap", help="Take a snapshot now")
    p.add_argument("label", nargs="*", help="Optional label")
    p.add_argument("--if-changed", action="store_true", help="Skip when nothing changed since the latest snapshot")
    p = sub.add_parser("list", help="List snapshots")
    p.add_argument("-n", type=int, default=20)
    p = sub.add_parser("diff", help="Show what changed between two snapshots")
    p.add_argument("old", nargs="?", default="latest~1")
    p.add_argument("new", nargs="?", default="latest")


def _ask_new_passphrase() -> Optional[str]:
    env = S.passphrase()
    if env:
        print("Using ARK_PASSPHRASE from your Hermes secrets.")
        return env
    if not sys.stdin.isatty():
        print("✗ No ARK_PASSPHRASE set and no terminal to ask for one. Set ARK_PASSPHRASE or pass --no-encryption.")
        return None
    while True:
        p1 = getpass.getpass("New repository passphrase (min 8 chars): ")
        if len(p1) < 8:
            print("Too short.")
            continue
        if getpass.getpass("Repeat passphrase: ") != p1:
            print("Passphrases do not match.")
            continue
        print("\nIMPORTANT: save this passphrase somewhere safe. Without it the backups cannot be restored.\n"
              "Also store it as ARK_PASSPHRASE so automatic snapshots can run, e.g.\n"
              "  echo 'ARK_PASSPHRASE=...' >> ~/.hermes/.env\n")
        return p1


def op_init(no_encryption: bool) -> str:
    st = S.load()
    be = S.backend(st)
    if Repository.exists(be):
        return f"A repository already exists at {st.repository}."
    pw = None if no_encryption else _ask_new_passphrase()
    if not no_encryption and not pw:
        return "✗ Repository not created."
    repo = Repository.init(be, pw)
    return (f"✓ Created {'encrypted ' if repo.encrypted else ''}Ark repository at {st.repository}\n"
            "  Next: `hermes ark snap` to take the first snapshot.")


def run_cli(args: argparse.Namespace) -> int:
    cmd = getattr(args, "ark_command", None) or "status"
    if cmd == "init":
        out = _guard(op_init, args.no_encryption)
    elif cmd == "status":
        out = _guard(op_status)
    elif cmd == "snap":
        out = _guard(op_snapshot, label=" ".join(args.label), trigger="cli", skip_if_unchanged=args.if_changed)
    elif cmd == "list":
        out = _guard(op_list, args.n)
    elif cmd == "diff":
        out = _guard(op_diff, args.old, args.new)
    else:
        out = f"unknown command {cmd}"
    print(out)
    return 1 if out.startswith(("✗", "🔒")) else 0
