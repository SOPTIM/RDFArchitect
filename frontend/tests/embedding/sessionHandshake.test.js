/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

import { describe, expect, test, vi } from "vitest";

import {
    installSessionHandshake,
    SESSION_REQUEST,
    SESSION_RESPONSE,
} from "$lib/embedding/session-handshake.js";

const SESSION = "3DA842A28B7F3DE99EA014ACBFBB420F";
const HOST_ORIGIN = "vscode-webview://0f1e2d3c";

/** A window stand-in that records listeners so a host message can be delivered by hand. */
function fakeWindow() {
    const listeners = new Map();
    return {
        listeners,
        addEventListener: (type, fn) => listeners.set(type, fn),
        removeEventListener: type => listeners.delete(type),
        async deliver(event) {
            await listeners.get("message")?.(event);
        },
    };
}

function hostRequest(overrides = {}) {
    return {
        data: { type: SESSION_REQUEST },
        origin: HOST_ORIGIN,
        source: { postMessage: vi.fn() },
        ...overrides,
    };
}

function install(target, options = {}) {
    return installSessionHandshake({
        enabled: true,
        embedded: true,
        fetchSessionId: async () => SESSION,
        target,
        ...options,
    });
}

describe("installSessionHandshake", () => {
    test("answers a host's request with the session id", async () => {
        const target = fakeWindow();
        install(target);
        const event = hostRequest();

        await target.deliver(event);

        expect(event.source.postMessage).toHaveBeenCalledWith(
            { type: SESSION_RESPONSE, id: SESSION },
            // Addressed to the asking origin — never "*", the id is a credential.
            HOST_ORIGIN,
        );
    });

    test("stays silent unless the deployment opted in", async () => {
        const target = fakeWindow();
        install(target, { enabled: false });

        expect(target.listeners.size).toBe(0);
    });

    test("stays silent when the app is not embedded", async () => {
        const target = fakeWindow();
        install(target, { embedded: false });

        expect(target.listeners.size).toBe(0);
    });

    test("ignores messages that are not a session request", async () => {
        const target = fakeWindow();
        install(target);
        const event = hostRequest({ data: { type: "something-else" } });

        await target.deliver(event);

        expect(event.source.postMessage).not.toHaveBeenCalled();
    });

    test("ignores a request from an opaque origin", async () => {
        const target = fakeWindow();
        install(target);
        const event = hostRequest({ origin: "null" });

        await target.deliver(event);

        expect(event.source.postMessage).not.toHaveBeenCalled();
    });

    test("says nothing when the session cannot be read", async () => {
        const target = fakeWindow();
        install(target, { fetchSessionId: async () => null });
        const event = hostRequest();

        await target.deliver(event);

        expect(event.source.postMessage).not.toHaveBeenCalled();
    });

    test("stops answering once uninstalled", async () => {
        const target = fakeWindow();
        const uninstall = install(target);

        uninstall();

        expect(target.listeners.size).toBe(0);
    });
});
