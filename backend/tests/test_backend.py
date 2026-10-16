import hashlib
import http.client
import io
import json
import re
import secrets
import socket
import sqlite3
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT.parent))

from backend.config import AuthConfig, Grant, load_auth
from backend.database import Database, MIGRATIONS, validate_profile, validate_subject
from backend.server import (
    ERRORS, JsonLogger, MAX_BODY, SandboxServer, bind_host, main,
)


def digest(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


def entry(token, subject="sandbox-test", scopes=None):
    return {
        "tokenSha256": digest(token), "subject": subject,
        "scopes": scopes if scopes is not None else ["profile:read", "profile:write"],
    }


class ConfigTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="test-config-", dir=BACKEND_ROOT)
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "auth.json"
        self.token = secrets.token_urlsafe(32)

    def load(self, data):
        self.path.write_text(json.dumps(data), encoding="utf-8")
        return load_auth(self.path)

    def test_valid_configuration_and_digest_only(self):
        config = self.load([entry(self.token)])
        self.assertTrue(config.available)
        self.assertEqual(config.authenticate(self.token).subject, "sandbox-test")
        self.assertNotIn(self.token, repr(config))
        self.assertIsNone(config.authenticate(secrets.token_urlsafe(32)))

    def test_constant_time_digest_comparison(self):
        config = self.load([entry(self.token), entry(secrets.token_urlsafe(32), "sandbox-b")])
        with patch("backend.config.hmac.compare_digest", wraps=__import__("hmac").compare_digest) as compare:
            self.assertIsNotNone(config.authenticate(self.token))
            self.assertEqual(compare.call_count, 2)
            self.assertEqual(len(compare.call_args.args[0]), 64)

    def test_missing_unreadable_and_environment(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertFalse(load_auth().available)
        self.assertFalse(load_auth(self.path).available)
        self.assertFalse(load_auth(self.directory.name).available)
        self.load([entry(self.token)])
        with patch.dict("os.environ", {"AUTH_CONFIG_FILE": str(self.path)}, clear=True):
            self.assertTrue(load_auth().available)

    def test_strict_configuration(self):
        valid = entry(self.token)
        variants = [
            {}, [], [None], [valid, valid],
            [{**valid, "unknown": False}],
            [{**valid, "tokenSha256": "A" * 64}],
            [{**valid, "tokenSha256": "0" * 63}],
            [{**valid, "tokenSha256": "not-a-digest"}],
            [{**valid, "subject": "real-user"}],
            [{**valid, "subject": "sandbox-"}],
            [{**valid, "subject": "sandbox-../../private"}],
            [{**valid, "subject": "sandbox-Ü"}],
            [{**valid, "subject": "sandbox-" + "a" * 57}],
            [{**valid, "scopes": []}],
            [{**valid, "scopes": ["profile:read", "profile:read"]}],
            [{**valid, "scopes": ["admin"]}],
            [{**valid, "scopes": [False]}],
            [{**valid, "scopes": "profile:read"}],
            [entry(secrets.token_urlsafe(32), f"sandbox-{i}") for i in range(129)],
        ]
        for data in variants:
            with self.subTest(data=str(data)[:80]):
                self.assertFalse(self.load(data).available)

    def test_invalid_json_and_size(self):
        for raw in (
            b"\xff", b"{", b"NaN", b"[Infinity]", b"[" * 1500,
            b'{"a":1,"a":2}', b" " * 65537,
        ):
            self.path.write_bytes(raw)
            self.assertFalse(load_auth(self.path).available)
        data = json.dumps([entry(self.token)])
        self.path.write_text(data.replace('"subject":', '"subject":"sandbox-x","subject":'))
        self.assertFalse(load_auth(self.path).available)

    def test_bearer_syntax_bounds(self):
        config = self.load([entry(self.token)])
        for token in ("", "x" * 31, "x" * 257, "a b" * 20, "é" * 40, None, 12):
            self.assertIsNone(config.authenticate(token))
        for length in (32, 256):
            token = secrets.token_urlsafe(192)[:length]
            self.assertIsNotNone(self.load([entry(token)]).authenticate(token))

    def test_bind_is_loopback_by_default_and_wildcard_explicit(self):
        self.assertEqual(bind_host({}), "127.0.0.1")
        self.assertEqual(bind_host({
            "BIND_HOST": "0.0.0.0", "ALLOW_INTERNAL_CONTAINER_BIND": "1"
        }), "0.0.0.0")
        for env in (
            {"BIND_HOST": "0.0.0.0"},
            {"BIND_HOST": "192.0.2.1"},
            {"BIND_HOST": "::"},
        ):
            with self.assertRaises(ValueError):
                bind_host(env)

    def test_startup_failure_has_no_traceback_or_configuration(self):
        output = io.StringIO()
        with patch.dict("os.environ", {"BIND_HOST": "secret-invalid-host"}, clear=True):
            with patch("sys.stdout", output):
                self.assertEqual(main(), 1)
        self.assertEqual(json.loads(output.getvalue()), {"event": "startup_failed"})

    def test_real_startup_without_auth_is_loopback_and_fail_closed(self):
        output = io.StringIO()
        database_path = Path(self.directory.name) / "startup.sqlite3"

        def make_server(address, database, auth, logger):
            self.assertEqual(address, ("127.0.0.1", 8080))
            self.assertFalse(auth.available)
            self.assertTrue(database.ready())
            server = SandboxServer(("127.0.0.1", 0), database, auth, logger)
            original_serve = server.serve_forever

            def check_live_server(**_):
                thread = threading.Thread(
                    target=original_serve, kwargs={"poll_interval": 0.01}
                )
                thread.start()
                try:
                    for path, expected in (
                        ("/v1/health", 200), ("/v1/sandbox/profile", 503),
                    ):
                        connection = http.client.HTTPConnection(
                            *server.server_address, timeout=3
                        )
                        try:
                            connection.request("GET", path)
                            response = connection.getresponse()
                            self.assertEqual(response.status, expected)
                            body = json.loads(response.read())
                            if expected == 200:
                                self.assertEqual(body, {
                                    "status": "ok", "apiVersion": "v1", "mode": "sandbox",
                                })
                        finally:
                            connection.close()
                finally:
                    server.shutdown()
                    thread.join(timeout=3)

            server.serve_forever = check_live_server
            return server

        with patch.dict("os.environ", {"DB_PATH": str(database_path)}, clear=True):
            with patch("backend.server.SandboxServer", side_effect=make_server):
                with patch("sys.stdout", output):
                    self.assertEqual(main(), 0)
        self.assertEqual(json.loads(output.getvalue().splitlines()[0]), {"event": "startup"})


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="test-db-", dir=BACKEND_ROOT)
        self.addCleanup(self.directory.cleanup)
        self.db = Database(Path(self.directory.name) / "sandbox.sqlite3")

    def test_migrations_idempotence_and_readiness(self):
        self.assertFalse(self.db.ready())
        self.db.migrate()
        self.db.put_profile("sandbox-a", {"responseStyle": "detailed"})
        self.db.migrate()
        self.assertTrue(self.db.ready())
        self.assertEqual(self.db.get_profile("sandbox-a"), {"responseStyle": "detailed"})
        with self.db.connect() as connection:
            self.assertEqual(connection.execute(
                "SELECT version FROM schema_migrations"
            ).fetchall(), [(1,)])

    def test_fresh_migration_failure_rolls_back_all_ddl(self):
        with self.assertRaises(sqlite3.Error):
            self.db.migrate(((1, ("CREATE TABLE partial (id INTEGER)", "INVALID SQL")),))
        with self.db.connect() as connection:
            self.assertEqual(connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall(), [])

    def test_later_migration_failure_preserves_previous_version(self):
        self.db.migrate()
        broken = MIGRATIONS + ((2, ("CREATE TABLE partial (id INTEGER)", "BAD SQL")),)
        with self.assertRaises(sqlite3.Error):
            self.db.migrate(broken)
        self.assertTrue(self.db.ready())
        with self.db.connect() as connection:
            self.assertIsNone(connection.execute(
                "SELECT name FROM sqlite_master WHERE name='partial'"
            ).fetchone())

    def test_unknown_or_gapped_schema_fails_closed(self):
        self.db.migrate()
        with self.db.connect() as connection:
            connection.execute("INSERT INTO schema_migrations VALUES (99)")
        self.assertFalse(self.db.ready())
        with self.assertRaises(ValueError):
            self.db.migrate()
        with self.assertRaises(ValueError):
            self.db.migrate(((2, ("SELECT 1",)),))

    def test_crud_and_isolation(self):
        self.db.migrate()
        self.assertIsNone(self.db.get_profile("sandbox-a"))
        for style in ("concise", "step_by_step", "detailed"):
            profile = {"responseStyle": style}
            self.assertEqual(self.db.put_profile("sandbox-a", profile), profile)
            self.assertEqual(self.db.get_profile("sandbox-a"), profile)
            self.assertIsNone(self.db.get_profile("sandbox-b"))
        self.db.put_profile("sandbox-b", {"responseStyle": "concise"})
        self.db.delete_profile("sandbox-a")
        self.db.delete_profile("sandbox-a")
        self.assertIsNone(self.db.get_profile("sandbox-a"))
        self.assertEqual(self.db.get_profile("sandbox-b"), {"responseStyle": "concise"})

    def test_model_validation(self):
        for profile in (
            {}, [], None, {"responseStyle": "free text"},
            {"responseStyle": []}, {"responseStyle": True},
            {"responseStyle": "concise", "subject": "sandbox-other"},
        ):
            with self.subTest(profile=profile), self.assertRaises(ValueError):
                validate_profile(profile)
        self.db.migrate()
        for subject in ("sandbox-'; DROP TABLE sandbox_profiles;--", "user-a", "sandbox--a"):
            with self.assertRaises(ValueError):
                self.db.put_profile(subject, {"responseStyle": "concise"})
            with self.assertRaises(ValueError):
                self.db.get_profile(subject)
            with self.assertRaises(ValueError):
                self.db.delete_profile(subject)
        self.assertTrue(self.db.ready())
        validate_subject("sandbox-a_1")

    def test_sqlite_constraints(self):
        self.db.migrate()
        with self.db.connect() as connection:
            for subject, style in (
                ("user-a", "concise"), ("sandbox--a", "concise"),
                ("sandbox-a/x", "concise"), ("sandbox-a", "other"),
            ):
                with self.assertRaises(sqlite3.IntegrityError):
                    connection.execute(
                        "INSERT INTO sandbox_profiles VALUES (?, ?)", (subject, style)
                    )

    def test_readiness_missing_schema_and_bad_file(self):
        self.db.migrate()
        with self.db.connect() as connection:
            connection.execute("DROP TABLE sandbox_profiles")
        self.assertFalse(self.db.ready())
        Path(self.db.path).write_bytes(b"not a SQLite database")
        self.assertFalse(self.db.ready())

    def test_connection_closes_and_failed_write_rolls_back(self):
        self.db.migrate()
        with self.assertRaises(ValueError):
            with self.db.connect() as connection:
                connection.execute("INSERT INTO sandbox_profiles VALUES ('sandbox-a', 'concise')")
                raise ValueError()
        self.assertIsNone(self.db.get_profile("sandbox-a"))
        with self.assertRaises(sqlite3.ProgrammingError):
            connection.execute("SELECT 1")


class HttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix="test-http-", dir=BACKEND_ROOT)
        cls.db = Database(Path(cls.directory.name) / "sandbox.sqlite3")
        cls.db.migrate()
        cls.tokens = {key: secrets.token_urlsafe(32) for key in ("a", "b", "read", "write")}
        cls.auth = AuthConfig(tuple(
            Grant(digest(cls.tokens[key]), subject, frozenset(scopes))
            for key, subject, scopes in (
                ("a", "sandbox-a", ("profile:read", "profile:write")),
                ("b", "sandbox-b", ("profile:read", "profile:write")),
                ("read", "sandbox-a", ("profile:read",)),
                ("write", "sandbox-a", ("profile:write",)),
            )
        ), True)
        cls.logs = io.StringIO()
        cls.server = SandboxServer(("127.0.0.1", 0), cls.db, cls.auth, JsonLogger(cls.logs))
        cls.thread = threading.Thread(
            target=cls.server.serve_forever, kwargs={"poll_interval": 0.01}
        )
        cls.thread.start()
        cls.address = cls.server.server_address

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)
        cls.directory.cleanup()

    def setUp(self):
        self.server.auth = self.auth
        with self.db.connect() as connection:
            connection.execute("DELETE FROM sandbox_profiles")

    def request(self, method="GET", path="/v1/sandbox/profile", key="a", body=None, headers=None):
        fields = dict(headers or {})
        if key is not None:
            fields["Authorization"] = "Bearer " + self.tokens[key]
        if isinstance(body, dict):
            body = json.dumps(body).encode()
            fields.setdefault("Content-Type", "application/json")
        connection = http.client.HTTPConnection(*self.address, timeout=7)
        try:
            connection.request(method, path, body=body, headers=fields)
            response = connection.getresponse()
            content = response.read()
            return response.status, dict(response.getheaders()), json.loads(content) if content else None
        finally:
            connection.close()

    def raw(self, payload):
        with socket.create_connection(self.address, timeout=7) as client:
            client.sendall(payload)
            chunks = []
            while True:
                try:
                    chunk = client.recv(8192)
                except ConnectionResetError:
                    break
                if not chunk:
                    break
                chunks.append(chunk)
        data = b"".join(chunks)
        self.assertTrue(data, "Expected a bounded HTTP response")
        head, content = data.split(b"\r\n\r\n", 1)
        return int(head.split(b" ")[1]), head, json.loads(content) if content else None

    def assert_error(self, result, status):
        self.assertEqual(result[0], status)
        self.assertEqual(set(result[2]), {"error", "requestId"})
        self.assertEqual(result[2]["error"], {
            "code": ERRORS[status][0], "message": ERRORS[status][1],
        })
        self.assertRegex(result[2]["requestId"], r"^[0-9a-f]{32}$")

    def test_public_exact_contracts_without_auth(self):
        self.assertEqual(self.request(path="/v1/health", key=None)[2], {
            "status": "ok", "apiVersion": "v1", "mode": "sandbox",
        })
        self.assertEqual(self.request(path="/v1/capabilities", key=None)[2], {
            "apiVersion": "v1", "mode": "sandbox", "features": {
                "assistant": False, "profileSync": False, "deviceActions": False,
            },
        })

    def test_crud_exact_contract_and_subject_isolation(self):
        self.assert_error(self.request(), 404)
        for style in ("concise", "step_by_step", "detailed"):
            result = self.request("PUT", body={"responseStyle": style})
            self.assertEqual(result[0], 200)
            self.assertEqual(result[2], {"responseStyle": style})
            self.assertEqual(self.request()[2], result[2])
            self.assert_error(self.request(key="b"), 404)
        self.assertEqual(self.request("DELETE", key="b")[0], 204)
        self.assertEqual(self.request()[0], 200)
        self.assertEqual(self.request("DELETE")[::2], (204, None))
        self.assertEqual(self.request("DELETE")[0], 204)
        self.assert_error(self.request(), 404)

    def test_authentication_and_scopes(self):
        for method in ("GET", "PUT", "DELETE"):
            self.assert_error(self.request(method, key=None), 401)
        self.assert_error(self.request("PUT", key="read", body={"responseStyle": "concise"}), 403)
        self.assert_error(self.request("DELETE", key="read"), 403)
        self.assert_error(self.request(key="write"), 403)
        self.assertEqual(self.request("PUT", key="write", body={"responseStyle": "concise"})[0], 200)
        self.assertEqual(self.request(key="read")[0], 200)
        self.assertEqual(self.request(key=None, headers={
            "Authorization": "bEaReR " + self.tokens["read"]
        })[0], 200)
        self.assertEqual(self.request("DELETE", key="write")[0], 204)
        for authorization in ("Basic fake", "Bearer " + "short", "Bearer " + "x" * 300, "Bearer " + secrets.token_urlsafe(32)):
            self.assert_error(self.request(key=None, headers={"Authorization": authorization}), 401)

    def test_authorization_precedes_body_read(self):
        for authorization, status in (("", 401), ("Bearer " + self.tokens["read"], 403)):
            request = (
                "PUT /v1/sandbox/profile HTTP/1.1\r\nHost: localhost\r\n"
                f"Authorization: {authorization}\r\n"
                "Content-Type: application/json\r\nContent-Length: 100\r\n\r\n"
            ).encode()
            started = time.monotonic()
            self.assert_error(self.raw(request), status)
            self.assertLess(time.monotonic() - started, 2)

    def test_config_unavailable_fail_closed_public_still_works(self):
        self.server.auth = AuthConfig()
        try:
            for method in ("GET", "PUT", "DELETE"):
                self.assert_error(self.request(method), 503)
            self.assertEqual(self.request(path="/v1/health", key=None)[0], 200)
            self.assertEqual(self.request(path="/v1/capabilities", key=None)[0], 200)
        finally:
            self.server.auth = self.auth

    def test_invalid_profile_json(self):
        for raw in (
            b"{}", b"[]", b"null", b"NaN", b'{"responseStyle":NaN}',
            b'{"responseStyle":"CONCISE"}', b'{"responseStyle":true}',
            b'{"responseStyle":42}', b'{"responseStyle":[]}',
            b'{"responseStyle":"arbitrary private text"}',
            b'{"responseStyle":"concise","subject":"sandbox-b"}',
            b'{"responseStyle":"concise","responseStyle":"detailed"}',
            b'{"responseStyle":"concise","feedback":"private"}',
            b'{"responseStyle":"concise","summary":"private"}',
            b"\xff", b"{", b"[" * 1000,
        ):
            with self.subTest(raw=raw[:90]):
                self.assert_error(self.request("PUT", body=raw, headers={"Content-Type": "application/json"}), 400)
        self.assert_error(self.request(), 404)

    def test_media_types(self):
        for media in ("text/plain", "application/json; charset=latin-1", "application/json; extra=1", ""):
            self.assert_error(self.request("PUT", body=b'{"responseStyle":"concise"}', headers={"Content-Type": media}), 415)
        self.assertEqual(self.request(
            "PUT", body=b'{"responseStyle":"concise"}',
            headers={"Content-Type": "Application/JSON; charset=UTF-8"}
        )[0], 200)
        self.assert_error(self.request(
            "PUT", body={"responseStyle": "concise"},
            headers={"Content-Encoding": "gzip"}
        ), 415)

    def test_exact_body_limit_and_truncated_body(self):
        body = b'{"responseStyle":"concise"}'
        body += b" " * (MAX_BODY - len(body))
        self.assertEqual(self.request(
            "PUT", body=body, headers={"Content-Type": "application/json"}
        )[0], 200)
        with socket.create_connection(self.address, timeout=3) as client:
            client.sendall((
                "PUT /v1/sandbox/profile HTTP/1.1\r\nHost: localhost\r\n"
                "Authorization: Bearer " + self.tokens["a"] + "\r\n"
                "Content-Type: application/json\r\nContent-Length: 30\r\n\r\n{}"
            ).encode())
            client.shutdown(socket.SHUT_WR)
            self.assertIn(b"400 Bad Request", client.recv(4096))

    def test_put_body_timeout_does_not_change_existing_profile(self):
        self.request("PUT", body={"responseStyle": "detailed"})
        with patch("backend.server.SOCKET_TIMEOUT", 0.1):
            self.assert_error(self.raw((
                "PUT /v1/sandbox/profile HTTP/1.1\r\nHost: localhost\r\n"
                "Authorization: Bearer " + self.tokens["a"] + "\r\n"
                "Content-Type: application/json\r\nContent-Length: 30\r\n\r\n{"
            ).encode()), 408)
        self.assertEqual(self.request()[2], {"responseStyle": "detailed"})

    def test_request_ids_and_safe_response_headers(self):
        first = self.request(path="/v1/health", key=None, headers={"X-Request-ID": "client-private"})
        second = self.request(path="/v1/health", key=None)
        self.assertRegex(first[1]["X-Request-ID"], r"^[0-9a-f]{32}$")
        self.assertNotEqual(first[1]["X-Request-ID"], second[1]["X-Request-ID"])
        self.assertEqual(first[1]["Connection"], "close")
        self.assertEqual(first[1]["Cache-Control"], "no-store")
        self.assertEqual(first[1]["X-Content-Type-Options"], "nosniff")
        self.assertNotIn("Server", first[1])
        error = self.request(key=None)
        self.assertEqual(error[1]["X-Request-ID"], error[2]["requestId"])
        self.assertEqual(error[1]["WWW-Authenticate"], "Bearer " + 'realm="sandbox"')

    def test_methods_and_unknown_routes(self):
        for method in ("POST", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"):
            result = self.request(method)
            if method == "HEAD":
                self.assertEqual(result[0], 405)
                self.assertIsNone(result[2])
            else:
                self.assert_error(result, 405)
            self.assertEqual(result[1]["Allow"], "DELETE, GET, PUT")
        for path in (
            "/v1/profile", "/v1/sandbox/profile/sandbox-b", "/v1/sandbox/profile?subject=sandbox-b",
            "/v1/assistant", "/v1/auth/token", "/v2/health", "http://private.example/v1/health",
        ):
            self.assert_error(self.request(path=path), 404)
        self.assert_error(self.request("PUT", path="/v1/health", body={}), 405)

    def test_framing_and_body_limits(self):
        base = (
            "PUT /v1/sandbox/profile HTTP/1.1\r\nHost: localhost\r\n"
            "Authorization: Bearer " + self.tokens["a"] + "\r\n"
            "Content-Type: application/json\r\n"
        )
        for framing, status in (
            ("", 411),
            ("Transfer-Encoding: chunked\r\n", 400),
            ("Content-Length: 2\r\nTransfer-Encoding: chunked\r\n", 400),
            ("Content-Length: 2\r\nContent-Length: 2\r\n", 400),
            ("Content-Length: 2, 2\r\n", 400),
            ("Content-Length: -1\r\n", 400),
            ("Content-Length: +2\r\n", 400),
            ("Content-Length: 02\r\n", 400),
            ("Content-Length: 999999999999999999999\r\n", 400),
            (f"Content-Length: {MAX_BODY + 1}\r\n", 413),
            ("Expect: 100-continue\r\nContent-Length: 2\r\n", 400),
        ):
            with self.subTest(framing=framing):
                self.assert_error(self.raw((base + framing + "\r\n").encode()), status)
        for method in ("GET", "DELETE"):
            self.assert_error(self.request(method, body=b"{}"), 400)
        self.assert_error(self.raw((base + "Content-Length: 1\r\n\r\n{}").encode()), 400)
        self.assert_error(self.raw((base + "Content-Length: 0\r\n\r\n").encode()), 400)

    def test_header_and_line_bounds(self):
        for suffix in (
            "X-Huge: " + "x" * 2048 + "\r\n",
            "".join(f"X-{i}: {'x' * 1000}\r\n" for i in range(9)),
            "".join(f"X-{i}: a\r\n" for i in range(33)),
        ):
            self.assert_error(self.raw((
                "GET /v1/health HTTP/1.1\r\nHost: localhost\r\n" + suffix + "\r\n"
            ).encode()), 431)
        self.assert_error(self.raw(("GET /" + "x" * 2100 + " HTTP/1.1\r\n\r\n").encode()), 431)

    def test_malformed_headers(self):
        for request in (
            b"GET /v1/health HTTP/1.1\nHost: localhost\n\n",
            b"GET /v1/health HTTP/1.1\r\n\r\n",
            b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\nHost: other\r\n\r\n",
            b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\n folded: a\r\n\r\n",
            b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\nBad : x\r\n\r\n",
            b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\nBad: \x00\r\n\r\n",
            b"GET /v1/health HTTP/2.0\r\nHost: localhost\r\n\r\n",
            b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\nBad: \xff\r\n\r\n",
        ):
            self.assert_error(self.raw(request), 400)

    def test_socket_timeout(self):
        with patch("backend.server.SOCKET_TIMEOUT", 0.1):
            self.assert_error(self.raw(b"GET /v1/health HTTP/1.1\r\n"), 408)

    def test_total_request_deadline(self):
        with patch("backend.server.REQUEST_DEADLINE", 0.25):
            with socket.create_connection(self.address, timeout=2) as client:
                started = time.monotonic()
                for _ in range(10):
                    try:
                        client.sendall(b"G")
                        time.sleep(0.05)
                    except OSError:
                        break
                self.assertEqual(client.recv(4096), b"")
                self.assertLess(time.monotonic() - started, 1)

    def test_concurrency_limit_rejects_without_spawning(self):
        acquired = 0
        try:
            while self.server.slots.acquire(blocking=False):
                acquired += 1
            self.assert_error(self.raw(b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\n\r\n"), 503)
        finally:
            for _ in range(acquired):
                self.server.slots.release()
        self.assertEqual(self.request(path="/v1/health")[0], 200)

    def test_readiness_and_database_failure_are_generic(self):
        with patch.object(self.db, "ready", return_value=False):
            for path in ("/v1/health", "/v1/capabilities", "/v1/sandbox/profile"):
                self.assert_error(self.request(path=path), 503)
        for operation, method in (("get_profile", "GET"), ("put_profile", "PUT"), ("delete_profile", "DELETE")):
            with patch.object(self.db, operation, side_effect=sqlite3.OperationalError("private SQL or path")):
                self.assert_error(self.request(method, body={"responseStyle": "concise"} if method == "PUT" else None), 503)
        with patch.object(self.db, "get_profile", side_effect=RuntimeError("private traceback")):
            self.assert_error(self.request(), 500)

    def test_log_privacy_allowlist(self):
        marker = "private-marker-not-for-logs"
        self.request(path="/private/" + marker, key=None, headers={
            "X-Request-ID": marker, "Authorization": "Bearer " + marker,
        })
        self.request("PUT", body=json.dumps({"responseStyle": marker}).encode(), headers={"Content-Type": "application/json"})
        self.request("MAGICPRIVATE", path="/v1/health", key=None)
        time.sleep(0.03)
        text = self.logs.getvalue()
        for forbidden in (marker, "Authorization", "sandbox-a", "127.0.0.1", "private SQL", "traceback", "/v1/", *self.tokens.values()):
            self.assertNotIn(forbidden, text)
        for line in text.splitlines():
            record = json.loads(line)
            self.assertLessEqual(set(record), {"event", "requestId", "route", "method", "status"})
            if "route" in record:
                self.assertIn(record["route"], {"health", "capabilities", "sandbox_profile", "unknown"})

    def test_logger_drops_untrusted_fields(self):
        output = io.StringIO()
        JsonLogger(output).emit(
            "private-event", requestId="private", route="/private", method="PRIVATE",
            status=200, token="private", headers="private", url="private",
        )
        self.assertEqual(json.loads(output.getvalue()), {
            "event": "request", "route": "unknown", "method": "other", "status": 200,
        })


class OpenApiTests(unittest.TestCase):
    def setUp(self):
        self.document = json.loads(
            (Path(__file__).parents[1] / "openapi.json").read_text(encoding="utf-8")
        )

    def test_all_references_resolve_and_only_sandbox_operations_exist(self):
        def visit(value):
            if isinstance(value, dict):
                if "$ref" in value:
                    reference = value["$ref"]
                    self.assertTrue(reference.startswith("#/"))
                    target = self.document
                    for part in reference[2:].split("/"):
                        target = target[part]
                    self.assertIsInstance(target, dict)
                for item in value.values():
                    visit(item)
            elif isinstance(value, list):
                for item in value:
                    visit(item)

        visit(self.document)
        self.assertEqual(self.document["openapi"], "3.1.0")
        self.assertEqual(set(self.document["paths"]), {
            "/v1/health", "/v1/capabilities", "/v1/sandbox/profile",
        })
        profile = self.document["paths"]["/v1/sandbox/profile"]
        self.assertEqual(set(profile) - {"description"}, {"get", "put", "delete"})
        for method in ("get", "put", "delete"):
            self.assertEqual(profile[method]["security"], [{"sandboxBearer": []}])
            self.assertEqual(profile[method]["x-required-scopes"], [
                "profile:read" if method == "get" else "profile:write"
            ])

    def test_exact_profiles_capabilities_and_error_contracts(self):
        schemas = self.document["components"]["schemas"]
        profile = schemas["Profile"]
        self.assertFalse(profile["additionalProperties"])
        self.assertEqual(profile["required"], ["responseStyle"])
        self.assertEqual(profile["properties"]["responseStyle"]["enum"], [
            "concise", "step_by_step", "detailed",
        ])
        features = schemas["Capabilities"]["properties"]["features"]
        self.assertEqual(set(features["required"]), {
            "assistant", "profileSync", "deviceActions",
        })
        self.assertTrue(all(item["const"] is False for item in features["properties"].values()))
        errors = schemas["Error"]["properties"]["error"]["oneOf"]
        documented = {
            (item["properties"]["code"]["const"], item["properties"]["message"]["const"])
            for item in errors
        }
        self.assertEqual(documented, set(ERRORS.values()))
        self.assertEqual(
            self.document["components"]["responses"]["Unauthorized"]["headers"]
            ["WWW-Authenticate"]["schema"]["const"],
            "Bearer " + 'realm="sandbox"',
        )

    def test_auth_response_sources_have_no_masking_placeholders(self):
        self.assertNotIn("*" * 6, (BACKEND_ROOT / "server.py").read_text())
        from backend.server import error_body, response_bytes
        response = response_bytes(401, "a" * 32, error_body(401, "a" * 32))
        self.assertNotIn(b"*" * 6, response)
        self.assertIn(("WWW-Authenticate: Bearer " + 'realm="sandbox"').encode(), response)


if __name__ == "__main__":
    unittest.main()
