# Hermes Ark 🛡

**Encrypted, versioned backups of your whole Hermes — with item-level restore.**

Hermes learns for months: memory, `SOUL.md`, self-written skills, cron jobs, sessions, profiles.
One bad update, an over-eager "clean up your memory", a dead disk or a reclaimed VPS, and it is gone.
`hermes backup` makes a one-off ZIP (with your API keys in plain text). Ark makes it **automatic,
encrypted, deduplicated and restorable piece by piece**.

> Status: work in progress for the Hermes Agent Plugin Hackathon (Nov 2026).

## What works today (v0.3)

- `hermes ark init` — create a repository, encrypted with AES-256-GCM (key from your passphrase via scrypt).
- `hermes ark snap [label]` — snapshot memory, SOUL, skills, config, cron, sessions (`state.db` copied safely while Hermes runs) and **every profile**.
- Deduplication — unchanged files cost nothing; large files are chunked so an edited database stores only the changed MiB.
- `hermes ark list` / `hermes ark diff [A] [B]` — see what changed (🧠 memory, 🛠 skills, ⚙ config…).
- `hermes ark show [snap]` — what a snapshot holds, grouped as memory, soul, skills, config, cron, sessions, plugins, per profile.
- **Item-level restore** — `hermes ark restore <what> [snap]` previews first and applies with `--yes`:
  `all` · `memory` · `soul` · `skills` · `skill:<name>` · `config` · `cron` · `sessions` · `plugin:<name>` · `file:<path>` · `<profile>/memory`.
  - A **safety snapshot** is taken before every restore, so a restore can itself be undone.
  - Every blob is fetched and integrity-checked **before the first write**: a damaged backup never leaves a half-restored home.
  - Writes are atomic, never follow symlinks out of the Hermes home, and the sessions database is only restored while the gateway is stopped.
  - Credentials that were not captured are never deleted by a restore.
- `/ark` in any chat (CLI, TUI, Desktop, Telegram, Discord…): `status`, `snap`, `list`, `diff`, `show`, `restore … yes`, `prune`.

### Autopilot (set and forget)

- **Checkpoint before every memory/skill write.** Ark wraps Hermes' `memory` and `skill_manage` tools
  (`tool_execution` middleware) and saves a *brain* snapshot (memory, SOUL, skills, config, cron) right
  before the change. Unchanged → nothing stored, so it costs a directory walk.
- **Health guard.** After the write, Ark compares memory and skills before vs after. When memory shrinks
  by more than half or several skills vanish in one call, the warning is added **inside the tool result**,
  so the agent sees it immediately and tells you, with the exact undo command:

  ```
  ⚠ Hermes Ark health guard:
    - memories/MEMORY.md shrank 92% (718 → 57 bytes) in a single change
    The previous version is saved in snapshot 20261105T111624556530Z-5531.
    To undo, the user can run: /ark restore memory 20261105T111624556530Z-5531 yes
  ```
- **Full snapshots in the background** after sessions, at most every `interval_minutes` (default 60) of activity.
- **Smart retention**, pruned daily: last 10 · hourly for a day · daily for a week · weekly for a month ·
  monthly for a year. Snapshots you label yourself are kept. `hermes ark prune [--dry-run]`, `hermes ark forget <id>`.
- **`ark_checkpoint` tool**: the agent saves a labelled snapshot before risky changes to Hermes itself
  (merging skills, bulk-editing memory, reorganising cron). Restoring stays a user decision.
- **Credentials are never captured** (`.env`, `auth.json`, vault files, pairing tokens, browser login stores) unless you enable `include_secrets` *and* the repository is encrypted.

## Roadmap (this week)

- [x] Item-level restore with an automatic safety snapshot first
- [x] Automatic snapshots before memory/skill writes and after sessions; smart retention
- [x] Health guard: alert when memory or skills suddenly shrink
- [ ] Off-site: S3-compatible storage (AWS, Cloudflare R2, Backblaze B2, MinIO), push/pull to a new machine
- [ ] `verify` — prove every snapshot can actually be restored
- [ ] Hermes Desktop timeline page

## Install (development)

```bash
git clone https://github.com/medomohamed22/hermes-ark ~/.hermes/plugins/hermes-ark
hermes plugins enable hermes-ark
echo 'ARK_PASSPHRASE=choose-a-long-passphrase' >> ~/.hermes/.env
hermes ark init && hermes ark snap first
```

## Disclosure

- Reads files under your Hermes home (and its profiles) to back them up; writes only to the repository folder
  (default `<hermes home>/plugin-data/hermes-ark/repo`, configurable).
- Wraps the `memory` and `skill_manage` tools (middleware) to snapshot before and compare after; adds a
  note to their result only when the health guard fires. Never blocks or alters the tool call itself.
- Runs a background thread (`hermes-ark-snapshot`) after sessions for the periodic full snapshot.
- No network access, no subprocesses, no telemetry.
- Credentials are excluded by default; the passphrase is read through Hermes' secret scope (`ARK_PASSPHRASE`) and never stored.

## Development

```bash
python -m pytest tests
hermes plugins validate . --install-deps
```

MIT licensed.
