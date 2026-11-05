"""An Ark repository: content-addressed blobs + one manifest per snapshot.

Layout (keys in a :class:`~arkcore.backends.Backend`)::

    ark.json                 repository config (format, id, encryption block - never a key)
    blobs/<aa>/<id>          file contents, chunked, compressed, optionally encrypted
    snapshots/<snapshot-id>  manifest (JSON, compressed, optionally encrypted)

Blobs are immutable and shared between snapshots, so a snapshot of an unchanged home stores
nothing but a small manifest.
"""

from __future__ import annotations

import json
import os
import secrets
import zlib
from datetime import datetime, timezone
from typing import Iterator, Optional

from . import crypto
from .backends import Backend, BackendError

FORMAT = 1
CONFIG_KEY = "ark.json"
_RAW, _ZLIB = b"R", b"Z"


class RepoError(Exception):
    pass


class LockedError(RepoError):
    """The repository is encrypted and no (or a wrong) passphrase was given."""


def new_snapshot_id(now: Optional[datetime] = None) -> str:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    # Microseconds keep ids in creation order even for several snapshots within one second.
    return now.strftime("%Y%m%dT%H%M%S") + f"{now.microsecond:06d}Z-" + secrets.token_hex(2)


class Repository:
    def __init__(self, backend: Backend, config: dict, keys: Optional[crypto.Keys]):
        self.backend = backend
        self.config = config
        self.keys = keys

    # ---- lifecycle -------------------------------------------------------------------------

    @classmethod
    def init(cls, backend: Backend, passphrase: Optional[str]) -> "Repository":
        if backend.exists(CONFIG_KEY):
            raise RepoError(f"a repository already exists at {backend.label}")
        config = {"format": FORMAT, "id": secrets.token_hex(8), "created": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                  "encryption": None}
        keys = None
        if passphrase:
            config["encryption"], keys = crypto.new_encryption_block(passphrase)
        backend.write(CONFIG_KEY, json.dumps(config, indent=2).encode())
        return cls(backend, config, keys)

    @classmethod
    def open(cls, backend: Backend, passphrase: Optional[str]) -> "Repository":
        try:
            config = json.loads(backend.read(CONFIG_KEY))
        except BackendError as exc:
            raise RepoError(f"no Ark repository at {backend.label} (run `hermes ark init`)") from exc
        except ValueError as exc:
            raise RepoError(f"repository config at {backend.label} is damaged") from exc
        if config.get("format") != FORMAT:
            raise RepoError(f"unsupported repository format {config.get('format')!r}")
        keys = None
        if config.get("encryption"):
            if not passphrase:
                raise LockedError("this repository is encrypted: set ARK_PASSPHRASE")
            try:
                keys = crypto.unlock(config["encryption"], passphrase)
            except crypto.ArkCryptoError as exc:
                raise LockedError("wrong passphrase for this repository") from exc
        return cls(backend, config, keys)

    @staticmethod
    def exists(backend: Backend) -> bool:
        return backend.exists(CONFIG_KEY)

    @property
    def encrypted(self) -> bool:
        return self.keys is not None

    @property
    def repo_id(self) -> str:
        return self.config["id"]

    def change_passphrase(self, new_passphrase: str) -> None:
        if not self.keys:
            raise RepoError("repository is not encrypted")
        self.config["encryption"] = crypto.rewrap(self.config["encryption"], self.keys, new_passphrase)
        self.backend.write(CONFIG_KEY, json.dumps(self.config, indent=2).encode())

    # ---- encoding --------------------------------------------------------------------------

    def _encode(self, data: bytes, aad: bytes) -> bytes:
        packed = zlib.compress(data, 6)
        body = _ZLIB + packed if len(packed) < len(data) else _RAW + data
        return crypto.seal(self.keys.enc, body, aad) if self.keys else body

    def _decode(self, payload: bytes, aad: bytes) -> bytes:
        if self.keys:
            payload = crypto.open_sealed(self.keys.enc, payload, aad)
        tag, body = payload[:1], payload[1:]
        if tag == _ZLIB:
            return zlib.decompress(body)
        if tag == _RAW:
            return body
        raise RepoError("unknown object encoding")

    # ---- blobs -----------------------------------------------------------------------------

    def blob_id(self, data: bytes) -> str:
        if self.keys:
            return self.keys.blob_id(data)
        import hashlib
        return hashlib.sha256(data).hexdigest()

    @staticmethod
    def _blob_key(blob_id: str) -> str:
        return f"blobs/{blob_id[:2]}/{blob_id}"

    def has_blob(self, blob_id: str) -> bool:
        return self.backend.exists(self._blob_key(blob_id))

    def put_blob(self, data: bytes) -> tuple[str, bool]:
        """Store *data*; returns (blob id, newly written?)."""
        blob_id = self.blob_id(data)
        if self.has_blob(blob_id):
            return blob_id, False
        self.backend.write(self._blob_key(blob_id), self._encode(data, blob_id.encode()))
        return blob_id, True

    def get_blob(self, blob_id: str) -> bytes:
        data = self._decode(self.backend.read(self._blob_key(blob_id)), blob_id.encode())
        if self.blob_id(data) != blob_id:
            raise RepoError(f"blob {blob_id[:12]} failed its integrity check")
        return data

    def iter_blob_ids(self) -> Iterator[str]:
        for key in self.backend.list("blobs"):
            yield key.rsplit("/", 1)[-1]

    def delete_blob(self, blob_id: str) -> None:
        self.backend.delete(self._blob_key(blob_id))

    # ---- snapshots -------------------------------------------------------------------------

    def save_manifest(self, manifest: dict) -> None:
        sid = manifest["id"]
        raw = json.dumps(manifest, separators=(",", ":"), sort_keys=True).encode()
        self.backend.write(f"snapshots/{sid}", self._encode(raw, f"snapshot:{sid}".encode()))

    def load_manifest(self, snapshot_id: str) -> dict:
        try:
            payload = self.backend.read(f"snapshots/{snapshot_id}")
        except BackendError as exc:
            raise RepoError(f"no snapshot {snapshot_id}") from exc
        return json.loads(self._decode(payload, f"snapshot:{snapshot_id}".encode()))

    def snapshot_ids(self) -> list[str]:
        """Oldest first (ids sort by time)."""
        return sorted(k.rsplit("/", 1)[-1] for k in self.backend.list("snapshots"))

    def delete_manifest(self, snapshot_id: str) -> None:
        self.backend.delete(f"snapshots/{snapshot_id}")

    def resolve(self, ref: str) -> str:
        """Accept a full id, a unique prefix, ``latest`` or ``latest~N``."""
        ids = self.snapshot_ids()
        if not ids:
            raise RepoError("the repository has no snapshots yet")
        if ref in ("latest", "last", "HEAD"):
            return ids[-1]
        if ref.startswith("latest~") and ref[7:].isdigit():
            n = int(ref[7:])
            if n >= len(ids):
                raise RepoError(f"only {len(ids)} snapshots exist")
            return ids[-1 - n]
        matches = [i for i in ids if i.startswith(ref) or i.endswith(ref)]
        if len(matches) == 1:
            return matches[0]
        raise RepoError(f"no snapshot matches {ref!r}" if not matches else f"{ref!r} matches {len(matches)} snapshots")


def default_passphrase() -> Optional[str]:
    """Only for the standalone engine/tests; the plugin resolves secrets through Hermes."""
    return os.environ.get("ARK_PASSPHRASE") or None
