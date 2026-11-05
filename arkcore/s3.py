"""S3-compatible storage backend (AWS S3, Cloudflare R2, Backblaze B2, MinIO, Wasabi, Oracle...).

Implemented with the standard library only: AWS Signature Version 4 request signing over
``urllib``. Only five operations are used — GET, PUT, HEAD, DELETE object and ListObjectsV2 —
and every object Ark uploads is already encrypted on this machine (when the repository is
encrypted), so the storage provider only ever sees ciphertext and HMAC names.
"""

from __future__ import annotations

import hashlib
import hmac
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterator, Optional

from .backends import BackendError, _check_key

EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()
_NS = "{http://s3.amazonaws.com/doc/2006-03-01/}"


def _quote(s: str, safe: str = "-_.~") -> str:
    return urllib.parse.quote(s, safe=safe)


def _hmac(key: bytes, msg: str) -> bytes:
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def sign_v4(method: str, host: str, path: str, query: dict[str, str], headers: dict[str, str],
            payload_hash: str, access_key: str, secret_key: str, region: str, now: datetime,
            service: str = "s3") -> dict[str, str]:
    """Return the headers to send (input headers + x-amz-date, x-amz-content-sha256, Authorization).

    *path* must already be URI-encoded (S3 does not double-encode it).
    """
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = amz_date[:8]
    hdrs = {k.lower(): str(v).strip() for k, v in headers.items()}
    hdrs["host"] = host
    hdrs["x-amz-date"] = amz_date
    hdrs["x-amz-content-sha256"] = payload_hash
    signed = sorted(hdrs)
    canonical_headers = "".join(f"{k}:{' '.join(hdrs[k].split())}\n" for k in signed)
    signed_headers = ";".join(signed)
    canonical_query = "&".join(f"{_quote(k)}={_quote(v)}" for k, v in sorted(query.items()))
    canonical_request = "\n".join([method, path, canonical_query, canonical_headers, signed_headers, payload_hash])
    scope = f"{datestamp}/{region}/{service}/aws4_request"
    string_to_sign = "\n".join(["AWS4-HMAC-SHA256", amz_date, scope, hashlib.sha256(canonical_request.encode()).hexdigest()])
    k = _hmac(("AWS4" + secret_key).encode(), datestamp)
    k = _hmac(_hmac(_hmac(k, region), service), "aws4_request")
    signature = hmac.new(k, string_to_sign.encode(), hashlib.sha256).hexdigest()
    hdrs["authorization"] = (f"AWS4-HMAC-SHA256 Credential={access_key}/{scope}, "
                             f"SignedHeaders={signed_headers}, Signature={signature}")
    return hdrs


@dataclass
class S3Config:
    endpoint: str            # https://<account>.r2.cloudflarestorage.com, https://s3.us-east-1.amazonaws.com, http://127.0.0.1:9000
    bucket: str
    prefix: str = ""         # folder inside the bucket, e.g. "hermes-ark/laptop"
    region: str = "auto"     # R2 uses "auto"; AWS needs the bucket's region; MinIO accepts "us-east-1"
    access_key: str = ""
    secret_key: str = ""
    addressing: str = "auto"  # auto | path | virtual
    timeout: float = 60.0
    page_size: int = 1000

    @classmethod
    def from_url(cls, url: str, endpoint: str = "", **kw) -> "S3Config":
        """``s3://bucket/optional/prefix``."""
        p = urllib.parse.urlparse(url)
        if p.scheme != "s3" or not p.netloc:
            raise BackendError(f"not an s3:// URL: {url!r}")
        if not endpoint:
            raise BackendError("an S3 endpoint is required (e.g. https://<account>.r2.cloudflarestorage.com)")
        return cls(endpoint=endpoint.rstrip("/"), bucket=p.netloc, prefix=p.path.strip("/"), **kw)


# Largest response Ark accepts: an object (<= MAX_OBJECT plus encryption overhead) or one listing page.
MAX_RESPONSE = (256 << 20) + 4096


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """S3 never needs redirects; following one would carry the signed Authorization header to
    whatever host a malicious or compromised endpoint names."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise BackendError(f"S3 endpoint tried to redirect to {newurl!r}; refusing")


_OPENER = urllib.request.build_opener(_NoRedirect())


def _parse_xml(data: bytes) -> ET.Element:
    """Parse an S3 XML response. A DTD has no business in S3 XML: refuse any, so entity tricks
    (billion laughs, external entities) never reach the parser."""
    head = data[:4096].upper()
    if b"<!DOCTYPE" in head or b"<!ENTITY" in data.upper():
        raise BackendError("S3 response contains a DTD; refusing to parse it")
    return ET.fromstring(data)


class S3Backend:
    def __init__(self, cfg: S3Config):
        if not cfg.access_key or not cfg.secret_key:
            raise BackendError("S3 credentials missing: set ARK_S3_ACCESS_KEY_ID and ARK_S3_SECRET_ACCESS_KEY")
        u = urllib.parse.urlparse(cfg.endpoint)
        if u.scheme not in ("https", "http") or not u.netloc:
            raise BackendError(f"invalid S3 endpoint {cfg.endpoint!r}")
        if u.scheme == "http" and u.hostname not in ("localhost", "127.0.0.1", "::1"):
            raise BackendError("refusing plain-http S3 endpoint outside localhost; use https")
        self.cfg = cfg
        self.scheme = u.scheme
        virtual = cfg.addressing == "virtual" or (cfg.addressing == "auto" and u.hostname.endswith("amazonaws.com"))
        self.host = f"{cfg.bucket}.{u.netloc}" if virtual else u.netloc
        self.base_path = "" if virtual else "/" + _quote(cfg.bucket, safe="")
        self.region = "us-east-1" if cfg.region in ("", "auto") and u.hostname.endswith("amazonaws.com") else (cfg.region or "auto")
        self.label = f"s3://{cfg.bucket}/{cfg.prefix}".rstrip("/")

    def _full_key(self, key: str) -> str:
        _check_key(key)
        return f"{self.cfg.prefix}/{key}" if self.cfg.prefix else key

    def _request(self, method: str, key: Optional[str] = None, query: Optional[dict] = None,
                 body: bytes = b"", ok=(200,)) -> tuple[int, bytes]:
        path = self.base_path + ("/" + _quote(key, safe="/-_.~") if key is not None else "/")
        query = query or {}
        payload_hash = hashlib.sha256(body).hexdigest() if body else EMPTY_SHA256
        # Explicit binary content type: without it urllib sends application/x-www-form-urlencoded,
        # and some S3-compatible servers then parse the body as a form and store an empty object.
        extra = {"content-length": str(len(body)), "content-type": "application/octet-stream"} if method == "PUT" else {}
        headers = sign_v4(method, self.host, path, query, extra, payload_hash, self.cfg.access_key,
                          self.cfg.secret_key, self.region, datetime.now(timezone.utc))
        qs = "&".join(f"{_quote(k)}={_quote(v)}" for k, v in sorted(query.items()))
        url = f"{self.scheme}://{self.host}{path}" + (f"?{qs}" if qs else "")
        headers.pop("host", None)
        req = urllib.request.Request(url, data=body if method == "PUT" else None, method=method, headers=headers)
        try:
            with _OPENER.open(req, timeout=self.cfg.timeout) as resp:
                data = resp.read(MAX_RESPONSE + 1)
                if len(data) > MAX_RESPONSE:
                    raise BackendError(f"S3 {method} {key or ''}: response larger than any Ark object; refusing")
                return resp.status, data
        except urllib.error.HTTPError as exc:
            data = exc.read(65536) or b""
            if exc.code in ok:
                return exc.code, data
            raise BackendError(f"S3 {method} {key or ''} failed: HTTP {exc.code} {_s3_error(data)}") from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise BackendError(f"S3 {method} {key or ''} failed: {exc}") from exc

    # ---- Backend protocol ----------------------------------------------------------------------

    def read(self, key: str) -> bytes:
        status, data = self._request("GET", self._full_key(key), ok=(200, 404))
        if status == 404:
            raise BackendError(f"missing object: {key}")
        return data

    def write(self, key: str, data: bytes) -> None:
        self._request("PUT", self._full_key(key), body=data)

    def exists(self, key: str) -> bool:
        status, _ = self._request("HEAD", self._full_key(key), ok=(200, 404))
        return status == 200

    def delete(self, key: str) -> None:
        self._request("DELETE", self._full_key(key), ok=(200, 204, 404))

    def list(self, prefix: str) -> Iterator[str]:
        full = self._full_key(prefix.rstrip("/")) + "/" if prefix else (self.cfg.prefix + "/" if self.cfg.prefix else "")
        strip = len(self.cfg.prefix) + 1 if self.cfg.prefix else 0
        token = None
        while True:
            q = {"list-type": "2", "prefix": full, "max-keys": str(self.cfg.page_size)}
            if token:
                q["continuation-token"] = token
            _, data = self._request("GET", None, query=q)
            root = _parse_xml(data)
            for c in root.iter(f"{_NS}Contents"):
                k = c.findtext(f"{_NS}Key") or ""
                yield k[strip:]
            if (root.findtext(f"{_NS}IsTruncated") or "").lower() != "true":
                return
            token = root.findtext(f"{_NS}NextContinuationToken")
            if not token:
                return


def _s3_error(data: bytes) -> str:
    try:
        root = _parse_xml(data)
        return f"{root.findtext('Code') or ''}: {root.findtext('Message') or ''}".strip(": ")
    except (ET.ParseError, BackendError):
        return data[:200].decode("utf-8", "replace")
