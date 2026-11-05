## What does this PR do?

Adds **Hermes Ark** to the plugin catalog (`plugin-catalog/hermes-ark.yaml`, one file), pinned to
[`f2d5378724134dd62010df4def48ad2e0dad761e`](https://github.com/medomohamed22/hermes-ark/commit/f2d5378724134dd62010df4def48ad2e0dad761e).
I am the owner of https://github.com/medomohamed22/hermes-ark.

Hermes Ark is a time machine for Hermes itself: encrypted, versioned backups of memory, SOUL, skills, config,
cron, sessions and every profile, with item-level restore.

- **Automatic:** a snapshot right before every `memory` / `skill_manage` write, plus periodic full snapshots after sessions.
- **Health guard:** when memory or skills suddenly shrink, a note goes into the tool result, so the agent tells the user at once, along with the exact `/ark restore …` command that undoes it.
- **Restore:** everything, one kind (memory / skills / config / cron), one skill, or one profile. A preview comes first and a safety snapshot is taken before every restore. Two-phase: all blobs are verified before the first write.
- **Off-site:** any S3-compatible storage (AWS, R2, B2, MinIO…) or a folder; `hermes ark pull` restores onto a new machine; `hermes ark verify` proves every snapshot restores.
- **Self-defense:** a prompt-injected agent cannot prune, delete, reconfigure or switch off Ark. This was found by testing with a real model: a hidden comment in a file made the agent run `hermes config set …auto_snapshots false`.
- **Surfaces used:** `tool_execution` middleware, an `on_session_end` hook, the `ark_checkpoint` tool, `ctx.register_cli_command` (`hermes ark …`), `ctx.register_command` (`/ark …`), and a Desktop page (`desktop/plugin.js`, plugin SDK only) with a `dashboard/plugin_api.py` router.

Built for the Hermes Agent Plugin Hackathon.

## Related Issue

None — new catalog entry.

## Type of Change

- [x] ✨ New feature (non-breaking change that adds functionality)

## Changes Made

- `plugin-catalog/hermes-ark.yaml` — new entry (`tier: community`, `category: tools`, `requires_hermes: ">=0.21.6"`, also declared in `plugin.yaml`).

## Disclosure (rule 13)

- **Reads:**
  - The Hermes home and its profiles, to back them up.
  - Credential files (`.env`, `auth.json`, vault, pairing, browser logins, Webapp session token) are skipped; they are read and backed up only if the user enables `include_secrets`, which also requires an encrypted repository.
  - Ark's own secrets (`ARK_PASSPHRASE`, `ARK_S3_ACCESS_KEY_ID`, `ARK_S3_SECRET_ACCESS_KEY`) come only through `agent.secret_scope.get_secret` and are never stored or logged. Ark never opens `.env` for them; a test asserts this.
- **Writes:**
  - Snapshots go to the repository folder (default `$HERMES_HOME/plugin-data/hermes-ark/repo`) and Ark's data folder.
  - The Hermes home is written only on a restore the user confirms (terminal, `/ark … yes`, or the Desktop dialog). The agent has no restore tool.
  - After an agent tool call changed Ark itself, Ark writes back only the previous values of: its own settings and enabled state (through `ctx.set_config` / `hermes_cli.config.save_config`), its repository key file, and its own plugin files. Never secrets.
- **Middleware:**
  - `tool_execution` runs on every tool call. It snapshots before memory/skill writes and adds a note when the guard fires.
  - It **refuses** calls that would delete, rewrite or reconfigure Ark's backups or settings, or disable Ark, returning a "BLOCKED by Hermes Ark" result. It refuses again a call that already damaged Ark.
  - Read-only Ark commands and all other calls run unchanged. `allow_agent_admin: true` lifts this. Hermes' own approvals are never bypassed.
- **Background threads:** periodic snapshot / retention / auto-push after sessions; a watchdog in the agent process that checks Ark every 10 s (small file reads).
- **Subprocess:** one, only after something disabled Ark while the agent was active: `python -m hermes_cli.main plugins enable hermes-ark`, with a fixed argv and no shell. It runs so the running gateway loads Ark again; a direct config write is the fallback.
- **Network:** only to the user-configured S3 endpoint, over HTTPS (plain http only for localhost). Redirects are refused (they would carry the signature), responses are size-capped, and XML with a DTD is refused.
- **Not included:** no telemetry, no self-updater, no Python dependencies beyond Hermes' own `cryptography`, no binaries.

## How to Test

1. `hermes plugins install medomohamed22/hermes-ark --enable` (or by catalog name once merged), then restart the gateway.
2. `hermes config set ARK_PASSPHRASE '<a passphrase>'`, then `hermes ark init` and `hermes ark snap first`.
3. In a chat, ask the agent to remove most memory entries. The tool result carries the health-guard note, and the agent relays the `/ark restore memory <id> yes` command. Run it, start a new chat, and the memory is back.
4. `hermes ark status` · `hermes ark list` · `hermes ark verify`, or open the **Ark** page in Hermes Desktop.

Validation at the pinned commit (fresh clone): `hermes plugins validate <clone> --install-deps` → **Validation passed**.
Every check is ✓, including `security scan — safe`, `no core override` and `desktop surface`, with no private-import warnings.
`python scripts/validate_plugin_catalog.py plugin-catalog/` → OK.

## Checklist

### Code

- [x] I've read the [Contributing Guide](https://github.com/NousResearch/hermes-agent/blob/main/CONTRIBUTING.md)
- [x] My commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
- [x] I searched for [existing PRs](https://github.com/NousResearch/hermes-agent/pulls) to make sure this isn't a duplicate
- [x] My PR contains **only** changes related to this feature (one catalog file)
- [x] The plugin's own suite passes at the pinned commit: 173 passed, 1 skipped. The skipped test needs a live S3 server; it was run against one during development. The suite includes adversarial and fuzz tests (path traversal, ciphertext bit-flips, hostile S3 servers, prompt-injection attacks on Ark itself).
- [x] I've tested on my platform: Linux (Hermes from source), end-to-end with a real model (gpt-oss-20b) driving the agent

### Documentation & Housekeeping

- [x] README with a disclosure section and SECURITY.md (threat model, limits) in the plugin repo
- [x] Cross-platform: Linux tested; Windows/macOS paths and locks handled in code (msvcrt/fcntl), not yet run on those systems

## Screenshots / Logs

![Ark page in Hermes Desktop](https://raw.githubusercontent.com/medomohamed22/hermes-ark/f2d5378724134dd62010df4def48ad2e0dad761e/docs/media/desktop-dark.png)
![The agent relays Ark's health guard](https://raw.githubusercontent.com/medomohamed22/hermes-ark/f2d5378724134dd62010df4def48ad2e0dad761e/docs/media/chat-guard.png)
![Restore preview](https://raw.githubusercontent.com/medomohamed22/hermes-ark/f2d5378724134dd62010df4def48ad2e0dad761e/docs/media/restore-preview.png)
