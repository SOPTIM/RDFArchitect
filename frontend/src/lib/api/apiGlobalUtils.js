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

import { BackendConnection } from "$lib/api/backend.js";
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

export async function getXSDPrimitives() {
    const res = await bec.getXSDPrimitives();
    return await res.json();
}

export async function getKnownFields() {
    const res = await bec.getKnownOntologyFields();
    return await res.json();
}
