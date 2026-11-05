import os
from datetime import datetime, timezone

import pytest

from arkcore.backends import BackendError, LocalBackend
from arkcore.repo import Repository
from arkcore.restore import Selector, apply_plan, plan_restore
from arkcore.s3 import EMPTY_SHA256, S3Backend, S3Config, sign_v4
from arkcore.snapshot import create_snapshot
from arkcore.sync import SyncError, sync, verify


# ---- SigV4: AWS' published example ("GET Object" with a Range header, S3 SigV4 docs) ---------

def test_sigv4_matches_aws_documentation_example():
    hdrs = sign_v4(
        "GET", "examplebucket.s3.amazonaws.com", "/test.txt", {}, {"Range": "bytes=0-9"}, EMPTY_SHA256,
        "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1",
        datetime(2013, 5, 24, tzinfo=timezone.utc))
    assert hdrs["authorization"] == (
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
        "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, "
        "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41")


def test_s3_config_and_safety_checks():
    cfg = S3Config.from_url("s3://my-bucket/hermes/laptop", endpoint="https://acc.r2.cloudflarestorage.com",
                            access_key="a", secret_key="b")
    assert (cfg.bucket, cfg.prefix) == ("my-bucket", "hermes/laptop")
    be = S3Backend(cfg)
    assert be.host == "acc.r2.cloudflarestorage.com" and be.base_path == "/my-bucket"   # path-style for R2
    aws = S3Backend(S3Config.from_url("s3://b", endpoint="https://s3.eu-west-1.amazonaws.com", region="eu-west-1",
                                      access_key="a", secret_key="b"))
    assert aws.host == "b.s3.eu-west-1.amazonaws.com" and aws.base_path == ""          # virtual-hosted for AWS
    with pytest.raises(BackendError):
        S3Backend(S3Config.from_url("s3://b", endpoint="http://example.com", access_key="a", secret_key="b"))
    with pytest.raises(BackendError):
        S3Backend(S3Config.from_url("s3://b", endpoint="https://x.example.com"))       # no credentials
    with pytest.raises(BackendError):
        S3Config.from_url("s3://b", endpoint="")


# ---- push / pull / mirror (folder remote) --------------------------------------------------

@pytest.fixture
def local(tmp_path):
    return LocalBackend(tmp_path / "local")


def test_push_then_pull_onto_new_machine(hermes_home, local, tmp_path):
    repo = Repository.init(local, "machine-pass-1")
    create_snapshot(repo, hermes_home, label="laptop")
    remote = LocalBackend(tmp_path / "usb")
    r1 = sync(local, remote)
    assert r1.copied > 0 and len(r1.snapshots_copied) == 1
    assert sync(local, remote).copied == 0                                   # idempotent

    # New machine: empty Hermes home, pull, restore everything.
    new_home = tmp_path / "new-machine-home"
    new_home.mkdir()
    new_local = LocalBackend(tmp_path / "new-local")
    sync(remote, new_local)
    repo2 = Repository.open(new_local, "machine-pass-1")
    apply_plan(repo2, plan_restore(repo2, repo2.resolve("latest"), new_home, Selector()), new_home)
    assert (new_home / "memories" / "MEMORY.md").read_text() == (hermes_home / "memories" / "MEMORY.md").read_text()
    assert (new_home / "skills" / "weather" / "SKILL.md").exists()
    assert (new_home / "profiles" / "work" / "memories" / "MEMORY.md").exists()
    assert not (new_home / ".env").exists()                                 # credentials were never uploaded


def test_sync_refuses_mixing_repositories(hermes_home, tmp_path):
    a = LocalBackend(tmp_path / "a")
    b = LocalBackend(tmp_path / "b")
    create_snapshot(Repository.init(a, None), hermes_home)
    create_snapshot(Repository.init(b, None), hermes_home)
    with pytest.raises(SyncError):
        sync(a, b)


def test_mirror_deletes_pruned_objects_but_never_from_empty_source(hermes_home, local, tmp_path):
    from arkcore.retention import Policy, prune
    repo = Repository.init(local, None)
    first = create_snapshot(repo, hermes_home).snapshot_id
    (hermes_home / "memories" / "MEMORY.md").write_text("changed " * 100)
    create_snapshot(repo, hermes_home)
    remote = LocalBackend(tmp_path / "remote")
    sync(local, remote)
    prune(repo, Policy(), only=[first])
    assert sync(local, remote).deleted == 0                                  # additive by default
    assert first in [k.rsplit("/", 1)[-1] for k in remote.list("snapshots")]
    res = sync(local, remote, mirror=True)
    assert res.deleted >= 2 and first not in [k.rsplit("/", 1)[-1] for k in remote.list("snapshots")]

    empty = LocalBackend(tmp_path / "empty")
    import shutil
    shutil.copy(tmp_path / "local" / "ark.json", tmp_path / "empty-ark.json")
    (tmp_path / "empty").mkdir()
    shutil.copy(tmp_path / "empty-ark.json", tmp_path / "empty" / "ark.json")   # same repo id, zero snapshots
    with pytest.raises(SyncError):
        sync(empty, remote, mirror=True)
    assert list(remote.list("snapshots"))                                    # remote untouched


def test_interrupted_push_never_exposes_unrestorable_snapshot(hermes_home, local, tmp_path, monkeypatch):
    repo = Repository.init(local, None)
    create_snapshot(repo, hermes_home)
    remote = LocalBackend(tmp_path / "remote")
    calls = {"n": 0}
    real_write = remote.write

    def flaky(key, data):
        calls["n"] += 1
        if calls["n"] == 3:
            raise BackendError("network dropped")
        real_write(key, data)

    monkeypatch.setattr(remote, "write", flaky)
    with pytest.raises(BackendError):
        sync(local, remote, workers=1)
    assert list(remote.list("snapshots")) == []                              # manifests go last
    monkeypatch.setattr(remote, "write", real_write)
    sync(local, remote)
    assert verify(Repository.open(remote, None)).ok


# ---- verify ----------------------------------------------------------------------------------

def test_verify_detects_missing_and_corrupt_blobs(hermes_home, local, tmp_path):
    repo = Repository.init(local, "pw-12345678")
    s1 = create_snapshot(repo, hermes_home).snapshot_id
    rep = verify(repo)
    assert rep.ok and rep.snapshots == 1 and rep.blobs_checked > 0
    blobs = sorted((tmp_path / "local" / "blobs").rglob("*"))
    files = [p for p in blobs if p.is_file()]
    files[0].unlink()
    data = bytearray(files[1].read_bytes())
    data[-1] ^= 1
    files[1].write_bytes(bytes(data))
    rep = verify(repo)
    assert not rep.ok and len(rep.missing) == 1 and len(rep.corrupt) == 1
    assert rep.damaged_snapshots() == [s1]
    quick = verify(repo, read_data=False)
    assert len(quick.missing) == 1 and not quick.corrupt                     # quick mode only checks existence


# ---- real S3 server (MinIO) when available ----------------------------------------------------

MINIO = os.environ.get("ARK_TEST_S3_ENDPOINT")


@pytest.mark.skipif(not MINIO, reason="set ARK_TEST_S3_ENDPOINT (+ keys, bucket) to run against a real S3 server")
def test_real_s3_roundtrip(hermes_home, local):
    cfg = S3Config.from_url(f"s3://{os.environ['ARK_TEST_S3_BUCKET']}/ark-test-{os.getpid()}", endpoint=MINIO,
                            region=os.environ.get("ARK_TEST_S3_REGION", "us-east-1"),
                            access_key=os.environ["ARK_TEST_S3_KEY"], secret_key=os.environ["ARK_TEST_S3_SECRET"])
    remote = S3Backend(cfg)
    repo = Repository.init(local, "pw-12345678")
    create_snapshot(repo, hermes_home)
    for i in range(3):                                                       # force several list pages? small but real
        (hermes_home / "skills" / f"s{i}").mkdir()
        (hermes_home / "skills" / f"s{i}" / "SKILL.md").write_text(f"# {i} " + "x" * 3000)
    create_snapshot(repo, hermes_home)
    res = sync(local, remote)
    assert res.copied > 5
    assert sync(local, remote).copied == 0
    paged = S3Backend(S3Config(**{**cfg.__dict__, "page_size": 2}))       # exercise continuation tokens
    assert sorted(paged.list("blobs")) == sorted(remote.list("blobs")) and len(list(paged.list("blobs"))) > 4
    rrepo = Repository.open(remote, "pw-12345678")
    assert verify(rrepo).ok
    assert len(rrepo.snapshot_ids()) == 2
    for k in list(remote.list("")):
        remote.delete(k)
    assert list(remote.list("")) == []
