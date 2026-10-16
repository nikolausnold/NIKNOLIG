"""Strict, fail-closed sandbox bearer configuration."""

import hashlib
import hmac
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path

TOKEN = re.compile(r"[A-Za-z0-9_-]{32,256}", re.ASCII)
SUBJECT = re.compile(r"sandbox-[a-z0-9][a-z0-9_-]{0,55}", re.ASCII)
DIGEST = re.compile(r"[0-9a-f]{64}", re.ASCII)
SCOPES = frozenset({"profile:read", "profile:write"})
MAX_CONFIG_BYTES = 65536


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def strict_json(raw):
    def reject_constant(_):
        raise ValueError("invalid JSON constant")

    return json.loads(
        raw, object_pairs_hook=strict_object, parse_constant=reject_constant
    )


@dataclass(frozen=True)
class Grant:
    digest: str
    subject: str
    scopes: frozenset[str]


@dataclass(frozen=True)
class AuthConfig:
    grants: tuple[Grant, ...] = ()
    available: bool = False

    def authenticate(self, token):
        if not isinstance(token, str) or TOKEN.fullmatch(token) is None:
            return None
        digest = hashlib.sha256(token.encode("ascii")).hexdigest()
        match = None
        for grant in self.grants:
            if hmac.compare_digest(digest, grant.digest):
                match = grant
        return match


def load_auth(path=None):
    """Missing, unreadable, or invalid configuration never enables access."""
    path = path if path is not None else os.environ.get("AUTH_CONFIG_FILE")
    if not path:
        return AuthConfig()
    try:
        with Path(path).open("rb") as source:
            raw = source.read(MAX_CONFIG_BYTES + 1)
        if len(raw) > MAX_CONFIG_BYTES:
            return AuthConfig()
        data = strict_json(raw.decode("utf-8"))
        if not isinstance(data, list) or not 1 <= len(data) <= 128:
            return AuthConfig()
        grants = []
        digests = set()
        for item in data:
            if not isinstance(item, dict) or set(item) != {
                "tokenSha256", "subject", "scopes"
            }:
                return AuthConfig()
            digest, subject, scopes = (
                item["tokenSha256"], item["subject"], item["scopes"]
            )
            if (
                not isinstance(digest, str)
                or DIGEST.fullmatch(digest) is None
                or digest in digests
                or not isinstance(subject, str)
                or SUBJECT.fullmatch(subject) is None
                or not isinstance(scopes, list)
                or not scopes
                or any(not isinstance(scope, str) for scope in scopes)
                or len(set(scopes)) != len(scopes)
                or not set(scopes) <= SCOPES
            ):
                return AuthConfig()
            digests.add(digest)
            grants.append(Grant(digest, subject, frozenset(scopes)))
        return AuthConfig(tuple(grants), True)
    except (OSError, ValueError, UnicodeError, RecursionError):
        return AuthConfig()
