"""Repository encryption: passphrase -> keys (scrypt), blobs sealed with AES-256-GCM.

Design
- One random 32-byte *master key* per repository encrypts everything. It is stored in the repo
  config wrapped (AES-GCM) under a key derived from the user's passphrase with scrypt, so changing
  the passphrase only re-wraps the master key.
- Blob ids are HMAC-SHA256(id_key, plaintext) when encrypted, so the repository (or a remote
  bucket) never reveals plain SHA-256 hashes of your files.
- Every sealed payload carries a magic + version header and the AES-GCM tag authenticates it:
  tampering or a wrong passphrase fails loudly, never yields garbage.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import os
from dataclasses import dataclass
from typing import Optional

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"ARK1"
_NONCE = 12
# scrypt cost: ~64 MiB / ~0.2-0.5 s on a laptop. Stored per repo so it can be raised later.
DEFAULT_KDF = {"name": "scrypt", "n": 2**16, "r": 8, "p": 1}


class ArkCryptoError(Exception):
    """Wrong passphrase, tampered data, or an unreadable payload."""


def _b64(b: bytes) -> str:
    return base64.b64encode(b).decode("ascii")


def _unb64(s: str) -> bytes:
    return base64.b64decode(s.encode("ascii"))


def derive_key(passphrase: str, salt: bytes, kdf: dict) -> bytes:
    if kdf.get("name") != "scrypt":
        raise ArkCryptoError(f"unsupported kdf {kdf.get('name')!r}")
    n, r, p = int(kdf["n"]), int(kdf["r"]), int(kdf["p"])
    # Bounds: a tampered ark.json must not be able to make unlocking take minutes or gigabytes.
    if not (2**10 <= n <= 2**20 and n & (n - 1) == 0 and 1 <= r <= 16 and 1 <= p <= 4):
        raise ArkCryptoError("repository key-derivation parameters are out of range (corrupted or tampered ark.json)")
    return hashlib.scrypt(passphrase.encode("utf-8"), salt=salt, n=n, r=r, p=p, maxmem=256 * 1024 * 1024, dklen=32)


def seal(key: bytes, plaintext: bytes, aad: bytes = b"") -> bytes:
    nonce = os.urandom(_NONCE)
    return MAGIC + nonce + AESGCM(key).encrypt(nonce, plaintext, aad or None)


def open_sealed(key: bytes, payload: bytes, aad: bytes = b"") -> bytes:
    if len(payload) < len(MAGIC) + _NONCE + 16 or not payload.startswith(MAGIC):
        raise ArkCryptoError("not an Ark encrypted payload")
    nonce = payload[len(MAGIC):len(MAGIC) + _NONCE]
    try:
        return AESGCM(key).decrypt(nonce, payload[len(MAGIC) + _NONCE:], aad or None)
    except InvalidTag as exc:
        raise ArkCryptoError("decryption failed (wrong passphrase or corrupted data)") from exc


@dataclass(frozen=True)
class Keys:
    """Working keys for an unlocked repository. ``None`` means the repo is unencrypted."""

    enc: bytes
    ids: bytes

    def blob_id(self, data: bytes) -> str:
        return hmac.new(self.ids, data, hashlib.sha256).hexdigest()


def new_encryption_block(passphrase: str, kdf: Optional[dict] = None) -> tuple[dict, Keys]:
    """Create a fresh master key wrapped under *passphrase*. Returns (config block, keys)."""
    if not passphrase:
        raise ArkCryptoError("an empty passphrase cannot protect a repository")
    kdf = dict(kdf or DEFAULT_KDF)
    salt = os.urandom(16)
    master = os.urandom(64)  # 32 bytes AES key + 32 bytes id/HMAC key
    wrapped = seal(derive_key(passphrase, salt, kdf), master, aad=b"ark-master")
    block = {"kdf": kdf, "salt": _b64(salt), "wrapped_key": _b64(wrapped)}
    return block, Keys(enc=master[:32], ids=master[32:])


def unlock(block: dict, passphrase: str) -> Keys:
    try:
        kek = derive_key(passphrase, _unb64(block["salt"]), block["kdf"])
        master = open_sealed(kek, _unb64(block["wrapped_key"]), aad=b"ark-master")
    except (KeyError, ValueError) as exc:
        raise ArkCryptoError("repository encryption config is damaged") from exc
    return Keys(enc=master[:32], ids=master[32:])


def rewrap(block: dict, keys: Keys, new_passphrase: str) -> dict:
    """Same master key, new passphrase (old one no longer works)."""
    if not new_passphrase:
        raise ArkCryptoError("an empty passphrase cannot protect a repository")
    salt = os.urandom(16)
    kdf = dict(block.get("kdf") or DEFAULT_KDF)
    wrapped = seal(derive_key(new_passphrase, salt, kdf), keys.enc + keys.ids, aad=b"ark-master")
    return {"kdf": kdf, "salt": _b64(salt), "wrapped_key": _b64(wrapped)}
