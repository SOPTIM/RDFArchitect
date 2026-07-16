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

/** * Mirrors the backend's `CGMESVersion` enum. Jackson serializes/deserializes * enums by default via their constant name, hence the values here match * `CGMESVersion.V2_4_15` / `CGMESVersion.V3_0` from the backend exactly. */
export const CGMESVersion = Object.freeze({
    V2_4_15: "V2_4_15",
    V3_0: "V3_0",
});
