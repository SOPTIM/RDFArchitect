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

import fs from "node:fs";
import path from "node:path";
import { afterEach, expect, test, vi } from "vitest";

import { createTurtleTokensProvider } from "$lib/monaco/textmate.js";

afterEach(() => vi.unstubAllGlobals());

test("a failed load of the grammar is tried again by the next editor", async () => {
    const wasm = fs.readFileSync(
        path.join("node_modules/vscode-oniguruma/release/onig.wasm"),
    );
    const fetchWasm = vi
        .fn()
        .mockRejectedValueOnce(new TypeError("offline"))
        .mockResolvedValueOnce(new Response(wasm));
    vi.stubGlobal("fetch", fetchWasm);

    await expect(createTurtleTokensProvider()).rejects.toThrow("offline");
    const provider = await createTurtleTokensProvider();

    expect(fetchWasm).toHaveBeenCalledTimes(2);
    expect(provider.tokenize).toBeInstanceOf(Function);
});
