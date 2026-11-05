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

## Cryptography

- A random 64-byte master key per repository: 32 bytes for AES-256-GCM, 32 bytes for HMAC-SHA256 object ids.
- The master key is stored only wrapped (AES-256-GCM) under a key derived from your passphrase with **scrypt**
  (N=2¹⁶, r=8, p=1 — parameters stored per repository). Changing the passphrase re-wraps the master key.
- Each object: `ARK1 || 12-byte random nonce || AES-GCM(ciphertext + tag)`, with the object id (or `snapshot:<id>`)
  as associated data, so an object cannot be swapped for another one.
- Blob ids are `HMAC-SHA256(id_key, plaintext)` — storage sees neither content nor plain content hashes.
- Library: `cryptography` (already a Hermes dependency). No custom primitives.

**Your passphrase is the only key.** Lose it and the backups cannot be restored; nobody (including Ark) can recover it.

## Restores are defensive

- All data is fetched and verified **before the first write**: a damaged backup aborts cleanly.
- Paths from a manifest must be plain relative POSIX paths: no `..`, no absolute paths, no Windows drive letters or
  backslashes, no NUL. Writes never follow a symlinked parent out of the Hermes home.
- Only permission bits are restored (`& 0o777`) — never setuid/setgid/sticky.
- Decompression is bounded (no decompression bombs from a tampered unencrypted repository).
- The sessions database is restored only while the gateway is stopped; runtime lock files are never touched.

## Who can trigger what

| Action | Who |
|---|---|
| Snapshot | automatic; the agent (`ark_checkpoint`); you (`hermes ark`, `/ark`, Desktop) |
| Restore | **only you**: terminal, `/ark restore … yes` in an allowed chat, or Desktop after a confirm dialog. The agent has no restore tool. |
| Push / verify / prune | you; push also runs automatically after background snapshots when a remote is set |

`/ark` follows the gateway's own access control: anyone your gateway lets talk to Hermes can use it, exactly like
they can use the terminal tools. The Desktop routes sit behind the dashboard's authentication.

## Limits (be aware)

- An **unencrypted** repository (`hermes ark init --no-encryption`) gives no confidentiality and no tamper detection.
  Ark warns when you push one off-site.
- Ark backs up Hermes itself, not your project folders (Hermes' `/rollback` checkpoints cover those).
- External memory providers (Honcho, Supermemory, …) keep data in their own services; Ark backs up their local
  configuration only.

## Reporting

Please report vulnerabilities privately via GitHub security advisories on this repository.
