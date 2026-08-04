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

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { CGMESVersion } from "$lib/models/cgmes-constants.js";

export class BackendConnection {
    fetch;
    url;

    constructor(fetch, url) {
        this.fetch = fetch;
        this.url = url;
    }

    async getClassInfo(
        datasetName,
        graphURI,
        classUUID,
        includeSuperClasses = false,
    ) {
        const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/${encodeURIComponent(classUUID)}${includeSuperClasses ? "?includeSuperClasses=true" : ""}`;
        return fetch(url, {
            method: "GET",
            mode: "cors",
            headers: new Headers({ "Content-Type": "application/json" }),
            credentials: "include",
        });
    }
}
