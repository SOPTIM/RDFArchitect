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

/** How often the backend is asked how far the job has come. */
const POLL_INTERVAL_MS = 300;

/** How many polls in a row may fail before the job is given up on. */
const MAX_FAILED_POLLS = 3;

/**
 * Follows a background import job until it is done and applies every status it reads to an
 * {@link ImportProgress}.
 *
 * <p>Polling is also what tells the backend that somebody is still there to answer its questions,
 * so a job waiting for a decision keeps being polled rather than being left alone. One timeout is
 * kept in flight at a time, so {@link stop} ends the polling for good and following a second job
 * abandons the first.
 */
export class ImportStatusPoller {
    #timeout = null;
    #failedPolls = 0;
    #run = null;

    /**
     * Follows a job until it finishes.
     *
     * @param {() => Promise<{data?: object, error?: unknown}>} readStatus reads the job's status
     * @param {object} progress the {@link ImportProgress} every status is applied to
     * @param {(status: object) => void} [onStatus] called after each status was applied
     */
    follow(readStatus, progress, onStatus = () => {}) {
        this.stop();
        const run = { readStatus, progress, onStatus };
        this.#run = run;
        this.#failedPolls = 0;
        this.#schedule(run);
    }

    /** Stops following. The job itself keeps running; only this side stops listening. */
    stop() {
        if (this.#timeout !== null) {
            clearTimeout(this.#timeout);
            this.#timeout = null;
        }
        this.#run = null;
    }

    #schedule(run) {
        this.#timeout = setTimeout(
            () => void this.#poll(run),
            POLL_INTERVAL_MS,
        );
    }

    async #poll(run) {
        if (this.#run !== run) {
            return;
        }
        const { data, error } = await run.readStatus();
        if (this.#run !== run) {
            return;
        }
        if (error || !data) {
            // A single missed poll is not worth giving up on, the job keeps running.
            this.#failedPolls += 1;
            if (this.#failedPolls > MAX_FAILED_POLLS) {
                run.progress.fail(
                    "The progress of the import could not be read.",
                );
                this.stop();
                return;
            }
            this.#schedule(run);
            return;
        }
        this.#failedPolls = 0;
        run.progress.apply(data);
        run.onStatus(data);
        if (run.progress.finished) {
            this.stop();
            return;
        }
        this.#schedule(run);
    }
}
