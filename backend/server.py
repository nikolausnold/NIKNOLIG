"""Bounded, close-after-one-request HTTP server for synthetic sandbox state."""

import json
import os
import re
import secrets
import socket
import socketserver
import sqlite3
import threading
import time

from .config import AuthConfig, load_auth, strict_json
from .database import Database, validate_profile

MAX_BODY = 1024
MAX_HEADERS = 8192
MAX_LINE = 2048
MAX_HEADER_COUNT = 32
SOCKET_TIMEOUT = 3.0
REQUEST_DEADLINE = 5.0
ROUTES = {
    "/v1/health": "health",
    "/v1/capabilities": "capabilities",
    "/v1/sandbox/profile": "sandbox_profile",
}
METHODS = {
    "health": {"GET"},
    "capabilities": {"GET"},
    "sandbox_profile": {"GET", "PUT", "DELETE"},
}
ERRORS = {
    400: ("invalid_request", "Invalid request."),
    401: ("unauthorized", "Authentication required."),
    403: ("forbidden", "Insufficient scope."),
    404: ("not_found", "Resource not found."),
    405: ("method_not_allowed", "Method not allowed."),
    408: ("request_timeout", "Request timed out."),
    411: ("length_required", "Content-Length required."),
    413: ("payload_too_large", "Request too large."),
    415: ("unsupported_media_type", "Use application/json."),
    431: ("headers_too_large", "Request headers too large."),
    500: ("internal_error", "Request could not be completed."),
    503: ("unavailable", "Sandbox service unavailable."),
}
REASONS = {
    200: "OK", 204: "No Content", 400: "Bad Request", 401: "Unauthorized",
    403: "Forbidden", 404: "Not Found", 405: "Method Not Allowed",
    408: "Request Timeout", 411: "Length Required", 413: "Payload Too Large",
    415: "Unsupported Media Type", 431: "Request Header Fields Too Large",
    500: "Internal Server Error", 503: "Service Unavailable",
}
HEADER_NAME = re.compile(r"[!#$%&'*+\-.^_`|~0-9A-Za-z]+", re.ASCII)
REQUEST_LINE = re.compile(r"([A-Z]{1,16}) ([!-~]{1,1024}) HTTP/1\.[01]\r\n")
LOG_EVENTS = frozenset({"request", "startup", "startup_failed", "capacity_rejected"})


class JsonLogger:
    def __init__(self, stream=None):
        import sys
        self.stream = stream if stream is not None else sys.stdout
        self.lock = threading.Lock()

    def emit(self, event, **fields):
        record = {"event": event if event in LOG_EVENTS else "request"}
        request_id = fields.get("requestId")
        if isinstance(request_id, str) and re.fullmatch(r"[0-9a-f]{32}", request_id):
            record["requestId"] = request_id
        if "route" in fields:
            route = fields["route"]
            record["route"] = route if route in {*ROUTES.values(), "unknown"} else "unknown"
        if "method" in fields:
            method = fields["method"]
            record["method"] = method if method in {"GET", "PUT", "DELETE"} else "other"
        status = fields.get("status")
        if type(status) is int and status in REASONS:
            record["status"] = status
        with self.lock:
            self.stream.write(json.dumps(record, separators=(",", ":")) + "\n")
            self.stream.flush()


class RequestError(Exception):
    def __init__(self, status):
        self.status = status


class SandboxServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, address, database, auth=None, logger=None, max_workers=16):
        self.database = database
        self.auth = auth if auth is not None else AuthConfig()
        self.logger = logger if logger is not None else JsonLogger()
        self.slots = threading.BoundedSemaphore(max_workers)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            try:
                request.settimeout(0.2)
                request_id = secrets.token_hex(16)
                body = error_body(503, request_id)
                request.sendall(response_bytes(503, request_id, body))
                self.logger.emit("capacity_rejected", status=503)
            except OSError:
                pass
            finally:
                self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            self.shutdown_request(request)

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        # Never let socketserver print tracebacks, client addresses, or request data.
        self.logger.emit("request", status=500)


def error_body(status, request_id):
    code, message = ERRORS[status]
    return {"error": {"code": code, "message": message}, "requestId": request_id}


def response_bytes(status, request_id, body=None, allow=None, head=False):
    payload = b"" if body is None else json.dumps(
        body, separators=(",", ":"), ensure_ascii=True
    ).encode("ascii")
    headers = [
        f"HTTP/1.1 {status} {REASONS[status]}",
        f"Content-Length: {len(payload)}",
        "Connection: close",
        "Cache-Control: no-store",
        "X-Content-Type-Options: nosniff",
        f"X-Request-ID: {request_id}",
    ]
    if body is not None:
        headers.append("Content-Type: application/json")
    if status == 401:
        headers.append("WWW-Authenticate: Bearer " + 'realm="sandbox"')
    if allow is not None:
        headers.append("Allow: " + ", ".join(sorted(allow)))
    return ("\r\n".join(headers) + "\r\n\r\n").encode("ascii") + (b"" if head else payload)


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request_id = secrets.token_hex(16)
        self.route = "unknown"
        self.method = "other"
        self.deadline = time.monotonic() + REQUEST_DEADLINE
        self.buffer = bytearray()
        self.allow = None
        status, body = 500, None
        try:
            method, target, headers = self.read_headers()
            self.method = method
            self.route = ROUTES.get(target, "unknown")
            status, body = self.dispatch(method, headers)
        except RequestError as error:
            status, body = error.status, error_body(error.status, self.request_id)
        except (TimeoutError, socket.timeout):
            status, body = 408, error_body(408, self.request_id)
        except (sqlite3.Error, OSError, ValueError, RecursionError):
            status, body = 503, error_body(503, self.request_id)
        except Exception:
            status, body = 500, error_body(500, self.request_id)
        try:
            self.set_timeout()
            self.request.sendall(
                response_bytes(
                    status, self.request_id, body, self.allow, self.method == "HEAD"
                )
            )
        except OSError:
            pass
        finally:
            self.server.logger.emit(
                "request", requestId=self.request_id, route=self.route,
                method=self.method, status=status,
            )

    def set_timeout(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError()
        self.request.settimeout(min(SOCKET_TIMEOUT, remaining))

    def receive(self, size):
        self.set_timeout()
        chunk = self.request.recv(size)
        if not chunk:
            raise RequestError(400)
        return chunk

    def line(self):
        while b"\n" not in self.buffer:
            if len(self.buffer) >= MAX_LINE:
                raise RequestError(431)
            # Do not consume any entity bytes before authorization.
            self.buffer.extend(self.receive(1))
        index = self.buffer.index(b"\n") + 1
        if index > MAX_LINE:
            raise RequestError(431)
        result = bytes(self.buffer[:index])
        del self.buffer[:index]
        if not result.endswith(b"\r\n"):
            raise RequestError(400)
        return result

    def read_headers(self):
        raw_line = self.line()
        try:
            match = REQUEST_LINE.fullmatch(raw_line.decode("ascii"))
        except UnicodeError:
            raise RequestError(400) from None
        if match is None:
            raise RequestError(400)
        method, target = match.groups()
        headers = {}
        size = len(raw_line)
        count = 0
        while True:
            line = self.line()
            size += len(line)
            if size > MAX_HEADERS:
                raise RequestError(431)
            if line == b"\r\n":
                break
            count += 1
            if count > MAX_HEADER_COUNT:
                raise RequestError(431)
            try:
                name, value = line[:-2].decode("ascii").split(":", 1)
            except (ValueError, UnicodeError):
                raise RequestError(400) from None
            if HEADER_NAME.fullmatch(name) is None:
                raise RequestError(400)
            name = name.lower()
            if name in headers or any(ord(char) < 32 or ord(char) == 127 for char in value):
                raise RequestError(400)
            headers[name] = value.strip(" ")
        if "host" not in headers or not headers["host"]:
            raise RequestError(400)
        if "transfer-encoding" in headers or "expect" in headers:
            raise RequestError(400)
        length = headers.get("content-length")
        if length is not None:
            if not re.fullmatch(r"0|[1-9][0-9]{0,9}", length):
                raise RequestError(400)
            if int(length) > MAX_BODY:
                raise RequestError(413)
        return method, target, headers

    def dispatch(self, method, headers):
        if self.route == "unknown":
            raise RequestError(404)
        if method not in METHODS[self.route]:
            self.allow = METHODS[self.route]
            raise RequestError(405)
        grant = None
        if self.route == "sandbox_profile":
            if not self.server.auth.available:
                raise RequestError(503)
            authorization = headers.get("authorization", "")
            if authorization[:7].lower() != "bearer ":
                raise RequestError(401)
            grant = self.server.auth.authenticate(authorization[7:])
            if grant is None:
                raise RequestError(401)
            scope = "profile:read" if method == "GET" else "profile:write"
            if scope not in grant.scopes:
                raise RequestError(403)
        if method != "PUT" and (
            headers.get("content-length", "0") != "0" or self.buffer
        ):
            raise RequestError(400)
        if not self.server.database.ready():
            raise RequestError(503)
        if self.route == "health":
            return 200, {"status": "ok", "apiVersion": "v1", "mode": "sandbox"}
        if self.route == "capabilities":
            return 200, {
                "apiVersion": "v1", "mode": "sandbox",
                "features": {
                    "assistant": False, "profileSync": False, "deviceActions": False
                },
            }
        if method == "GET":
            profile = self.server.database.get_profile(grant.subject)
            if profile is None:
                raise RequestError(404)
            return 200, profile
        if method == "DELETE":
            self.server.database.delete_profile(grant.subject)
            return 204, None
        if "content-length" not in headers:
            raise RequestError(411)
        media = headers.get("content-type", "")
        if "content-encoding" in headers or re.fullmatch(
            r"application/json(?:\s*;\s*charset=utf-8)?", media, re.IGNORECASE
        ) is None:
            raise RequestError(415)
        length = int(headers["content-length"])
        if len(self.buffer) > length:
            raise RequestError(400)
        while len(self.buffer) < length:
            self.buffer.extend(self.receive(length - len(self.buffer)))
        try:
            profile = strict_json(bytes(self.buffer).decode("utf-8"))
            validate_profile(profile)
        except (ValueError, UnicodeError, RecursionError):
            raise RequestError(400) from None
        return 200, self.server.database.put_profile(grant.subject, profile)


def bind_host(environment):
    host = environment.get("BIND_HOST", "127.0.0.1")
    if host not in {"127.0.0.1", "0.0.0.0"}:
        raise ValueError("unsupported bind address")
    if host == "0.0.0.0" and environment.get("ALLOW_INTERNAL_CONTAINER_BIND") != "1":
        raise ValueError("wildcard binding not enabled")
    return host


def main():
    logger = JsonLogger()
    try:
        host = bind_host(os.environ)
        port = int(os.environ.get("PORT", "8080"))
        if not 1 <= port <= 65535:
            raise ValueError("invalid port")
        database = Database(os.environ.get("DB_PATH", "backend/data/sandbox.sqlite3"))
        database.migrate()
        auth = load_auth()
        with SandboxServer((host, port), database, auth, logger) as server:
            logger.emit("startup")
            server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        return 0
    except Exception:
        logger.emit("startup_failed")
        return 1
    return 0
