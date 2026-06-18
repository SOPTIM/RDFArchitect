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

import { describeError } from "./StoreLogging";
import { getPrimitiveDatatypes, Uri } from "../api/generated";

const LOG_PREFIX = "[xsdPrimitivesStore]";

let cache: Uri[] | null = null;
let pending: Promise<Uri[]> | null = null;

/**
 * Fetches the XSD primitive datatypes once and caches the result for the
 * lifetime of the browser session. Concurrent callers share a single
 * in-flight request. Returns an empty array if the request fails.
 */
export async function loadXsdPrimitives(): Promise<Uri[]> {
    if (cache !== null) return cache;
    if (pending !== null) return pending;

    console.log(`${LOG_PREFIX} Loading XSD primitive datatypes`);

    pending = (async () => {
        try {
            const { data, error } = await getPrimitiveDatatypes();

            if (error) {
                console.error(
                    `${LOG_PREFIX} Failed to load XSD primitive datatypes:`,
                    await describeError(error),
                );
                return [];
            }

            const result = data ?? [];
            cache = result;
            console.log(
                `${LOG_PREFIX} Loaded ${result.length} XSD primitive datatypes`,
            );
            return result;
        } catch (err) {
            console.error(
                `${LOG_PREFIX} Unexpected error while loading XSD primitive datatypes:`,
                err,
            );
            return [];
        } finally {
            pending = null;
        }
    })();

    return pending;
}

/**
 * Returns the cached XSD primitive datatypes, or `null` if they have not
 * been loaded yet. Does not trigger a fetch — call `loadXsdPrimitives()`
 * first if you need to ensure data is available.
 */
export function getXSDPrimitives(): Uri[] | null {
    return cache;
}
