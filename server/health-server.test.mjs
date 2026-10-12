import assert from "node:assert/strict";
import { once } from "node:events";
import { after, before, describe, it } from "node:test";
import { createHealthServer } from "./health-server.mjs";

describe("health API", () => {
    let server;
    let baseUrl;

    before(async () => {
        server = createHealthServer({ allowedOrigin: "https://nikolausnold.github.io" });
        server.listen(0, "127.0.0.1");
        await once(server, "listening");
        baseUrl = `http://127.0.0.1:${server.address().port}`;
    });

    after(async () => {
        server.close();
        await once(server, "close");
    });

    it("returns only a fixed, cache-disabled health document", async () => {
        const response = await fetch(`${baseUrl}/v1/health`);
        assert.equal(response.status, 200);
        assert.equal(response.headers.get("cache-control"), "no-store");
        assert.equal(response.headers.get("x-content-type-options"), "nosniff");
        assert.deepEqual(await response.json(), {
            status: "ok",
            service: "niknolig-api",
            apiVersion: 1
        });
    });

    it("allows only the configured website origin", async () => {
        const allowed = await fetch(`${baseUrl}/v1/health`, {
            headers: { Origin: "https://nikolausnold.github.io" }
        });
        assert.equal(allowed.status, 200);
        assert.equal(allowed.headers.get("access-control-allow-origin"), "https://nikolausnold.github.io");

        const denied = await fetch(`${baseUrl}/v1/health`, {
            headers: { Origin: "https://attacker.example" }
        });
        assert.equal(denied.status, 403);
    });

    it("rejects unsupported methods and paths", async () => {
        const method = await fetch(`${baseUrl}/v1/health`, { method: "POST" });
        assert.equal(method.status, 405);
        assert.equal(method.headers.get("allow"), "GET");

        const path = await fetch(`${baseUrl}/v1/accounts`);
        assert.equal(path.status, 404);
    });

    it("rejects request bodies rather than parsing or storing them", async () => {
        const response = await fetch(`${baseUrl}/v1/health`, {
            method: "POST",
            body: "must not be accepted"
        });
        assert.equal(response.status, 413);
    });

    it("requires a valid HTTPS origin configuration", () => {
        assert.throws(
            () => createHealthServer({ allowedOrigin: "http://nikolausnold.github.io" }),
            /must be an HTTPS origin/
        );
        assert.throws(
            () => createHealthServer({ allowedOrigin: "https://nikolausnold.github.io/path" }),
            /must be an HTTPS origin/
        );
    });
});
