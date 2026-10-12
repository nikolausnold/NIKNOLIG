import { createServer } from "node:http";
import { pathToFileURL } from "node:url";

const HEALTH_RESPONSE = JSON.stringify({
    status: "ok",
    service: "niknolig-api",
    apiVersion: 1
});

export function createHealthServer({ allowedOrigin = "" } = {}) {
    if (allowedOrigin) {
        const origin = new URL(allowedOrigin);
        if (origin.protocol !== "https:" || origin.origin !== allowedOrigin) {
            throw new Error("NIKNOLIG_ALLOWED_ORIGIN must be an HTTPS origin without a path.");
        }
    }

    const server = createServer((request, response) => {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        if (request.url !== "/v1/health") {
            response.writeHead(404).end();
            return;
        }

        const requestOrigin = request.headers.origin;
        if (requestOrigin && requestOrigin !== allowedOrigin) {
            request.resume();
            response.writeHead(403).end();
            return;
        }

        if (requestOrigin) {
            response.setHeader("Access-Control-Allow-Origin", allowedOrigin);
            response.setHeader("Vary", "Origin");
        }

        if (
            Number(request.headers["content-length"] || 0) > 0 ||
            request.headers["transfer-encoding"]
        ) {
            request.resume();
            response.writeHead(413, { Connection: "close" }).end();
            return;
        }

        if (request.method !== "GET") {
            request.resume();
            response.setHeader("Allow", "GET");
            response.writeHead(405).end();
            return;
        }

        response.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
        response.end(HEALTH_RESPONSE);
    });

    server.headersTimeout = 5_000;
    server.requestTimeout = 5_000;
    server.keepAliveTimeout = 5_000;
    server.maxHeadersCount = 32;
    return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const port = Number.parseInt(process.env.PORT || "8080", 10);
    const host = process.env.HOST || "127.0.0.1";
    const allowedOrigin = process.env.NIKNOLIG_ALLOWED_ORIGIN || "";
    if (!Number.isInteger(port) || port < 1 || port > 65_535) {
        throw new Error("PORT must be a valid TCP port.");
    }

    const server = createHealthServer({ allowedOrigin });
    server.listen(port, host);
}
