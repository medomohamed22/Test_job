# Security model

## What Ark protects against

| Threat | Protection |
|---|---|
| Losing Hermes' memory, skills, config (bad update, an agent "cleanup", deleted files) | Automatic snapshots before every memory/skill write and periodically; item-level restore with a safety snapshot first |
| Losing the machine (dead disk, reclaimed VPS) | Off-site copy (`push`), `pull` + `restore all` on a new machine |
| Storage provider or anyone with the bucket/USB drive reading your data | AES-256-GCM encryption of every object on this machine before upload |
| Tampering with the off-site copy | Every encrypted object is authenticated (GCM tag, object id as associated data); a modified object fails to decrypt and `verify` names the affected snapshots |
| Silent corruption | Blobs are re-hashed on read; `verify` checks every blob a restore needs |
| Credential leakage through backups | Credential stores are excluded by default; including them requires an encrypted repository |
| A prompt-injected agent switching Ark off or deleting backups | Self-defense: such tool calls are refused; disguised ones raise a tamper alarm and Ark puts its settings back |

## Cryptography

- A random 64-byte master key per repository: 32 bytes for AES-256-GCM, 32 bytes for HMAC-SHA256 object ids.
- The master key is stored only wrapped (AES-256-GCM) under a key derived from your passphrase with **scrypt**
  (N=2¹⁶, r=8, p=1 — parameters stored per repository). Changing the passphrase re-wraps the master key.
- Each object: `ARK1 || 12-byte random nonce || AES-GCM(ciphertext + tag)`, with the object id (or `snapshot:<id>`)
  as associated data, so an object cannot be swapped for another one.
- Blob ids are `HMAC-SHA256(id_key, plaintext)` — storage sees neither content nor plain content hashes.
- Library: `cryptography` (already a Hermes dependency). No custom primitives.

**Your passphrase is the only key.** Lose it and the backups cannot be restored; nobody (including Ark) can recover it.

## Off-site storage is untrusted

- With `ARK_PASSPHRASE` set, Ark refuses to open a repository that claims to be unencrypted, and a sync can never
  switch encryption off on the other side: a tampered `ark.json` cannot downgrade you to planted plaintext snapshots.
- Key-derivation parameters read from `ark.json` are bounded, so a tampered config cannot hang or exhaust memory on unlock.
- S3: redirects are refused (they would carry the signed `Authorization` header to another host), responses are
  size-capped, and XML containing a DTD/entities is refused before parsing.
- Object keys from a remote listing are validated before anything is written locally (no `..`, no absolute paths).

## Restores are defensive

- Two phases: every file is streamed from the backup into a temp file beside its target and each blob verified;
  only when all are ready are they renamed into place. A damaged backup, a full disk or a folder where a file
  belongs aborts cleanly and leaves the home untouched. Memory stays bounded by one blob, whatever the file size.
- Paths from a manifest must be plain relative POSIX paths: no `..`, no absolute paths, no Windows drive letters or
  backslashes, no NUL. Writes never follow a symlinked parent out of the Hermes home.
- Only permission bits are restored (`& 0o777`) — never setuid/setgid/sticky.
- Decompression is bounded (no decompression bombs from a tampered unencrypted repository).
- Containment checks are path-aware (`~/.hermes-evil` is not inside `~/.hermes`).
- Files that could not be read at snapshot time, Ark's own repository, and Hermes' skill bookkeeping
  (`skills/.curator_ledger.jsonl`) are never deleted by a restore of that kind.
- Names that reach the model (skill folders, labels) are sanitized: no newlines, quotes, brackets or control characters.
- The sessions database is restored only while the gateway is stopped; runtime lock files are never touched.

## Who can trigger what

| Action | Who |
|---|---|
| Snapshot | automatic; the agent (`ark_checkpoint`); you (`hermes ark`, `/ark`, Desktop) |
| Restore | **only you**: terminal, `/ark restore … yes` in an allowed chat, or Desktop after a confirm dialog. The agent has no restore tool. |
| Push / verify / prune | you; push also runs automatically after background snapshots when a remote is set |

## Self-defense against the agent

Tested with a real model (gpt-oss-20b) inside Hermes: a hidden comment in a file the user only asked to have
summarised made the agent run `hermes config set plugins.entries.hermes-ark.settings.auto_snapshots false`
and `hermes ark prune` — without telling the user. A backup the agent can switch off does not protect against
the agent, so Ark's `tool_execution` middleware now guards Ark itself:

- **Refused before running** (terminal, execute_code, write_file, patch, also through `tool_call`):
  `hermes ark` prune/forget/init/pull/repair/`restore --yes`/`push --mirror`; disabling or uninstalling the plugin;
  `hermes config set` on Ark's settings or on `plugins.enabled`; anything touching Ark's data folder, repository,
  folder remote, plugin files or `ARK_*` secrets; `config.yaml`/`.env` edits that change Ark's part (judged on the
  resulting file, so unrelated config edits still work). Read-only commands (`status`, `list`, `diff`, `show`,
  `verify`, `snap`, a restore preview) and `ark_checkpoint` stay available. The block message tells the model it is
  probably a prompt injection and to warn the user; the attempt is logged (⛔ in `hermes ark status`, Desktop banner).
- **Tamper alarm after running**: text matching cannot see through base64 or a script file, so every command/file
  tool call is fingerprinted before and after (Ark's settings and enabled state, `ARK_*` secret names, the
  repository, its snapshots, Ark's code). Any change is reported in the tool result and on the Desktop (🚨), and
  **Ark's settings are put back automatically**.
- The agent cannot grant itself access: `allow_agent_admin` (off by default) is itself an Ark setting.
- Snapshots the agent labels via `ark_checkpoint` are pinned only up to the newest 20, so a looping agent cannot fill
  the disk with history retention may never delete. Your own labelled snapshots stay pinned forever.

Limits: an agent with an approved, unrestricted shell runs as your user; it could in principle disable Ark in ways
that leave no trace until the next tool call. Off-site copies (`push`, ideally to storage with object versioning or
retention lock) are the backstop for that.

`/ark` follows the gateway's own access control: anyone your gateway lets talk to Hermes can use it, exactly like
they can use the terminal tools. The Desktop routes sit behind the dashboard's authentication.

## Testing

`tests/test_security.py` reproduces each issue above (each test fails on the code before its fix), plus fuzzing:
5,000 random restore paths, 5,000 selector strings and 300 random bit-flips in ciphertext. `bandit` and `ruff`
(security rules) run clean apart from justified, documented items. `tests/test_selfdefense.py` replays the prompt-
injection attacks (30 hostile commands, 16 legitimate ones that must still work, file edits, disguised attacks).
The same scenarios were run end-to-end in Hermes with a free real model (memory wipe, skill deletion, injected
file, hex-encoded Python, a base64 script): each was blocked or caught and undone. The Desktop API answers 401 to every
unauthenticated request, cross-origin included.

## Limits (be aware)

- An **unencrypted** repository (`hermes ark init --no-encryption`) gives no confidentiality and no tamper detection.
  Ark warns when you push one off-site.
- Ark backs up Hermes itself, not your project folders (Hermes' `/rollback` checkpoints cover those).
- External memory providers (Honcho, Supermemory, …) keep data in their own services; Ark backs up their local
  configuration only.

## Reporting

Please report vulnerabilities privately via GitHub security advisories on this repository.
