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

import { expect, test, vi } from "vitest";

import { replaceGraphWithGraphString } from "../../src/lib/api/generated/index.ts";
import { plainTextBody } from "../../src/lib/api/plainTextBody.js";

const TURTLE = '@prefix ex: <http://example.org/> .\nex:a ex:b "c" .\n';

async function sentRequest(options) {
    const fetch = vi
        .fn()
        .mockResolvedValue(new Response(null, { status: 200 }));
    await replaceGraphWithGraphString({
        baseUrl: "http://backend.test",
        path: { workspaceName: "ws", schemaName: "eq" },
        fetch,
        ...options,
    });
    return fetch.mock.calls[0][0];
}

test("plainTextBody_sentWithTheGeneratedClient_arrivesUnquotedAsTextPlain", async () => {
    const request = await sentRequest(plainTextBody(TURTLE));

    expect(await request.text()).toBe(TURTLE);
    expect(request.headers.get("Content-Type")).toBe("text/plain");
});

test("plainTextBody_notUsed_generatedClientSendsTheTextQuoted", async () => {
    const request = await sentRequest({ body: TURTLE });

    expect(await request.text()).toBe(JSON.stringify(TURTLE));
});
