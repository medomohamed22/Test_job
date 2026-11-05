# Hermes Ark 🛡

**Encrypted, versioned backups of your whole Hermes — with item-level restore.**

Hermes learns for months: memory, `SOUL.md`, self-written skills, cron jobs, sessions, profiles.
One bad update, an over-eager "clean up your memory", a dead disk or a reclaimed VPS, and it is gone.
`hermes backup` makes a one-off ZIP (with your API keys in plain text). Ark makes it **automatic,
encrypted, deduplicated and restorable piece by piece**.

> Status: work in progress for the Hermes Agent Plugin Hackathon (Nov 2026).

## What works today (v0.4)

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

### Off-site copy and moving to a new machine

- **Remote** = `s3://bucket/prefix` on any S3-compatible storage (AWS S3, **Cloudflare R2 — 10 GB free**,
  Backblaze B2, Wasabi, MinIO, Oracle Object Storage…) or a **folder** (USB drive, NAS, Syncthing/Dropbox folder).
  S3 requests are signed with AWS Signature V4 implemented on the standard library (checked against AWS'
  published test vector) — no boto3. With an encrypted repository the provider only ever sees ciphertext.
- `hermes ark push` uploads what the remote lacks (blobs first, manifests last, so an interrupted upload never
  exposes a snapshot that cannot be restored). Automatic after each background snapshot (`auto_push`).
  `--mirror` also removes data pruned locally — refused across different repositories or from an empty one,
  so a fresh machine can never wipe your off-site copy.
- **New machine / dead disk:**
  ```bash
  hermes plugins install hermes-ark && hermes plugins enable hermes-ark
  # put ARK_PASSPHRASE, ARK_S3_ACCESS_KEY_ID, ARK_S3_SECRET_ACCESS_KEY in ~/.hermes/.env
  hermes ark pull --remote s3://my-bucket/hermes --endpoint https://<account>.r2.cloudflarestorage.com
  hermes ark restore all          # preview
  hermes ark restore all --yes    # your memory, SOUL, skills, cron, sessions and profiles are back
  ```
- `hermes ark verify [--remote] [--quick]` proves every snapshot can be restored: it downloads, decrypts and
  re-hashes every blob a restore would need, and names the damaged snapshots and the newest intact one.
  *A backup you have never tested is not a backup.* Also `/ark verify` from chat.

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
- [x] Off-site: S3-compatible storage or a folder; push/pull to a new machine
- [x] `verify` — prove every snapshot can actually be restored
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
- Network: only when you configure an S3 `remote` — HTTPS requests to that endpoint (plain http only for
  localhost). Uploaded objects are encrypted on this machine when the repository is encrypted.
  S3 keys are read through Hermes' secret scope (`ARK_S3_ACCESS_KEY_ID`, `ARK_S3_SECRET_ACCESS_KEY`), never stored.
- No subprocesses, no telemetry.
- Credentials are excluded by default; the passphrase is read through Hermes' secret scope (`ARK_PASSPHRASE`) and never stored.

## Development

```bash
python -m pytest tests
# optional: run the S3 test against a real server
ARK_TEST_S3_ENDPOINT=http://127.0.0.1:5055 ARK_TEST_S3_BUCKET=ark-test ARK_TEST_S3_KEY=... ARK_TEST_S3_SECRET=... python -m pytest tests
hermes plugins validate . --install-deps
```

MIT licensed.
