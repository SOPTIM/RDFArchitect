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



export const userSettings = createUserSettings();function getCookie(name) {
    if (typeof document === "undefined") return {};
    const match = document.cookie
        .split("; ")
        .find(row => row.startsWith(name + "="));
    return match ? JSON.parse(decodeURIComponent(match.split("=")[1])) : {};
}

function setCookie(name, value) {
    if (typeof document === "undefined") return;
    const encoded = encodeURIComponent(JSON.stringify(value));
    const expires = new Date();
    expires.setFullYear(expires.getFullYear() + 10);
    document.cookie = `${name}=${encoded}; expires=${expires.toUTCString()}; path=/; SameSite=Lax`;
}

function createUserSettings() {
    const COOKIE_NAME = "RDFA_USER_SETTINGS";

    let settings = $state(getCookie(COOKIE_NAME));

    if (typeof document !== "undefined" && Object.keys(settings).length === 0) {
        const loaded = getCookie(COOKIE_NAME);
        Object.assign(settings, loaded);
    }

    return {
        get(key, defaultValue = null) {
            return settings[key] ?? defaultValue;
        },
        set(key, value) {
            settings[key] = value;
            setCookie(COOKIE_NAME, settings);
        },
        get all() {
            return settings;
        },
    };
}
