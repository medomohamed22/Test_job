"""One writer at a time: an inter-process file lock plus an in-process thread lock."""

from __future__ import annotations

import os
import threading
import time
from contextlib import contextmanager
from pathlib import Path

_THREAD_LOCK = threading.RLock()


class LockBusy(Exception):
    pass


@contextmanager
def repo_lock(lock_path: Path, timeout: float = 30.0):
    """Exclusive lock on *lock_path*. Raises :class:`LockBusy` after *timeout* seconds."""
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + timeout
    if not _THREAD_LOCK.acquire(timeout=max(timeout, 0.01)):
        raise LockBusy("another Ark operation is running in this process")
    fh = open(lock_path, "a+b")
    try:
        while True:
            try:
                _lock(fh)
                break
            except OSError:
                if time.monotonic() >= deadline:
                    raise LockBusy("another Ark operation is running (repository locked)")
                time.sleep(0.2)
        try:
            yield
        finally:
            _unlock(fh)
    finally:
        fh.close()
        _THREAD_LOCK.release()


if os.name == "nt":  # pragma: no cover - exercised on Windows
    import msvcrt

    def _lock(fh):
        fh.seek(0)
        msvcrt.locking(fh.fileno(), msvcrt.LK_NBLCK, 1)

    def _unlock(fh):
        fh.seek(0)
        msvcrt.locking(fh.fileno(), msvcrt.LK_UNLCK, 1)
else:
    import fcntl

    def _lock(fh):
        fcntl.flock(fh.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)

    def _unlock(fh):
        fcntl.flock(fh.fileno(), fcntl.LOCK_UN)
