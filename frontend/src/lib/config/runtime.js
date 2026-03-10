/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { env as publicEnv } from "$env/dynamic/public";

import { browser } from "$app/environment";

const runtimeConfig = browser ? window.__RDFARCHITECT_CONFIG__ : {};

export const PUBLIC_BACKEND_URL = readPublicValue("PUBLIC_BACKEND_URL");

export const PUBLIC_APP_VERSION = readPublicValue("PUBLIC_APP_VERSION");
export const PUBLIC_COMMIT_SHA = readPublicValue("PUBLIC_COMMIT_SHA");
export const PUBLIC_REPOSITORY_URL = readPublicValue("PUBLIC_REPOSITORY_URL");
export const PUBLIC_DEPLOYMENT_ENVIRONMENT = readPublicValue(
    "PUBLIC_DEPLOYMENT_ENVIRONMENT",
);

function readPublicValue(key) {
    const runtimeValue = runtimeConfig?.[key];
    if (typeof runtimeValue === "string" && runtimeValue.trim()) {
        return runtimeValue.trim();
    }

    const envValue = publicEnv[key];
    return typeof envValue === "string" ? envValue.trim() : "";
}
