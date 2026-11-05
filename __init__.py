"""Hermes Ark — encrypted, versioned backups of your whole Hermes.

Registers:
  * ``hermes ark …``  terminal command (init, status, snap, list, diff)
  * ``/ark …``        in-session command on every surface (CLI, TUI, Desktop, Telegram, Discord…)

The storage engine lives in ``arkcore`` and has no Hermes imports, so it is unit-tested on its own.
"""

from __future__ import annotations

import logging

from . import cli, settings

logger = logging.getLogger(__name__)


def register(ctx) -> None:
    settings.bind(ctx)
    ctx.register_cli_command(
        name="ark",
        help="Encrypted, versioned backups of your Hermes (init, snap, list, diff)",
        setup_fn=cli.setup_parser,
        handler_fn=cli.run_cli,
        description="Hermes Ark: deduplicated, encrypted snapshots of memory, SOUL, skills, config, cron, sessions and profiles.",
    )
    ctx.register_command(
        "ark",
        handler=cli.handle_slash,
        description="Backups: status, snap, list, diff",
        args_hint="[status|snap|list|diff]",
    )
