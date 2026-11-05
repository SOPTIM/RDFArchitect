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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ImportStatusPoller } from "../../src/lib/utils/importStatusPoller.js";

/** A progress that is finished as soon as it has been given a status saying so. */
function progress() {
    return {
        finished: false,
        apply(status) {
            this.finished = status.finished;
        },
        fail() {},
    };
}

describe("ImportStatusPoller", () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it("stops polling once the job has finished", async () => {
        const readStatus = vi.fn(async () => ({ data: { finished: true } }));
        new ImportStatusPoller().follow(readStatus, progress());

        await vi.advanceTimersByTimeAsync(30 * 60_000);

        expect(readStatus).toHaveBeenCalledTimes(1);
    });

    it("keeps a finished job alive until stopped, the backend forgetting it otherwise", async () => {
        const readStatus = vi.fn(async () => ({ data: { finished: true } }));
        const poller = new ImportStatusPoller();
        poller.follow(readStatus, progress(), undefined, { keepAlive: true });

        // Ten minutes is how long the backend keeps a finished job nobody asks about.
        await vi.advanceTimersByTimeAsync(10 * 60_000);
        const polls = readStatus.mock.calls.length;
        poller.stop();
        await vi.advanceTimersByTimeAsync(10 * 60_000);

        expect(polls).toBeGreaterThanOrEqual(10);
        expect(readStatus).toHaveBeenCalledTimes(polls);
    });

    it("does not apply the status again while only keeping the job alive", async () => {
        const followed = progress();
        const apply = vi.spyOn(followed, "apply");
        new ImportStatusPoller().follow(
            async () => ({ data: { finished: true } }),
            followed,
            undefined,
            { keepAlive: true },
        );

        await vi.advanceTimersByTimeAsync(5 * 60_000);

        expect(apply).toHaveBeenCalledTimes(1);
    });
});
