"""Storage backends: where a repository's objects live.

A backend is a flat key/value store of immutable-ish objects (``ark.json``,
``blobs/ab/<id>``, ``snapshots/<id>``). The local backend writes atomically
(temp file + fsync + rename) so a crash never leaves a half-written object
under its final name.
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path, PurePosixPath
from typing import Iterator, Protocol


class BackendError(Exception):
    pass


class Backend(Protocol):
    label: str

    def read(self, key: str) -> bytes: ...
    def write(self, key: str, data: bytes) -> None: ...
    def exists(self, key: str) -> bool: ...
    def delete(self, key: str) -> None: ...
    def list(self, prefix: str) -> Iterator[str]: ...


def _check_key(key: str) -> str:
    p = PurePosixPath(key)
    if not key or p.is_absolute() or any(part in ("", ".", "..") for part in p.parts) or "\\" in key:
        raise BackendError(f"invalid object key: {key!r}")
    return key


class LocalBackend:
    """A directory on this machine (an external drive, a synced folder, a NAS mount...)."""

    def __init__(self, root: Path | str):
        self.root = Path(root).expanduser()
        self.label = str(self.root)

    def _path(self, key: str) -> Path:
        return self.root.joinpath(*PurePosixPath(_check_key(key)).parts)

    def read(self, key: str) -> bytes:
        try:
            return self._path(key).read_bytes()
        except FileNotFoundError as exc:
            raise BackendError(f"missing object: {key}") from exc

    def write(self, key: str, data: bytes) -> None:
        dest = self._path(key)
        dest.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(prefix=".tmp-", dir=dest.parent)
        try:
            with os.fdopen(fd, "wb") as f:
                f.write(data)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, dest)
        except BaseException:
            try:
                os.unlink(tmp)
            except OSError:
                pass
            raise

    def exists(self, key: str) -> bool:
        return self._path(key).is_file()

    def delete(self, key: str) -> None:
        try:
            self._path(key).unlink()
        except FileNotFoundError:
            pass

    def list(self, prefix: str) -> Iterator[str]:
        base = self._path(prefix.rstrip("/")) if prefix else self.root
        if not base.is_dir():
            return
        for path in sorted(base.rglob("*")):
            if path.is_file() and not path.name.startswith(".tmp-"):
                yield path.relative_to(self.root).as_posix()
