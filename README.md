# Hermes Ark 🛡

**Encrypted, versioned backups of your whole Hermes — with item-level restore.**

Hermes learns for months: memory, `SOUL.md`, self-written skills, cron jobs, sessions, profiles.
One bad update, an over-eager "clean up your memory", a dead disk or a reclaimed VPS, and it is gone.
`hermes backup` makes a one-off ZIP (with your API keys in plain text). Ark makes it **automatic,
encrypted, deduplicated and restorable piece by piece**.

> Status: work in progress for the Hermes Agent Plugin Hackathon (Nov 2026).

## What works today (v0.1)

- `hermes ark init` — create a repository, encrypted with AES-256-GCM (key from your passphrase via scrypt).
- `hermes ark snap [label]` — snapshot memory, SOUL, skills, config, cron, sessions (`state.db` copied safely while Hermes runs) and **every profile**.
- Deduplication — unchanged files cost nothing; large files are chunked so an edited database stores only the changed MiB.
- `hermes ark list` / `hermes ark diff [A] [B]` — see what changed (🧠 memory, 🛠 skills, ⚙ config…).
- `/ark` in any chat (CLI, TUI, Desktop, Telegram, Discord…): `status`, `snap`, `list`, `diff`.
- **Credentials are never captured** (`.env`, `auth.json`, vault files, pairing tokens, browser login stores) unless you enable `include_secrets` *and* the repository is encrypted.

## Roadmap (this week)

- [ ] Item-level restore (`restore memory`, `restore skill:<name>`) with an automatic safety snapshot first
- [ ] Automatic snapshots: before memory/skill writes, at session end, on a schedule; smart retention
- [ ] Health guard: alert when memory or skills suddenly shrink
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
- No network access, no subprocesses, no background processes, no telemetry (v0.1).
- Credentials are excluded by default; the passphrase is read through Hermes' secret scope (`ARK_PASSPHRASE`) and never stored.

## Development

```bash
python -m pytest tests
hermes plugins validate . --install-deps
```

MIT licensed.
