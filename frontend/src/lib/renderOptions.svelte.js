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

import { userSettings } from "./userSettings.svelte.js";

export const RENDER_OPTION_DEFAULTS = {
    includeEnumEntries: true,
    includeAttributes: true,
    includeAssociations: true,
    includeInheritance: true,
    includeRelationsToExternalPackages: true,
    includePropertiesFromOtherProfiles: false,
    showInheritedProperties: false,
    useColoredPropertiesInMergedView: true,
};

export const GRAPH_FILTER_KEYS = [
    "includeEnumEntries",
    "includeAttributes",
    "includeAssociations",
    "includeInheritance",
    "includeRelationsToExternalPackages",
    "includePropertiesFromOtherProfiles",
];

export const renderOptions = createRenderOptions();

function createRenderOptions() {
    let overrides = $state({});

    return {
        get(key) {
            if (key in overrides) {
                return overrides[key];
            }
            return userSettings.get(key, RENDER_OPTION_DEFAULTS[key]);
        },
        set(key, value) {
            const defaultValue = userSettings.get(
                key,
                RENDER_OPTION_DEFAULTS[key],
            );
            if (value === defaultValue) {
                overrides = Object.fromEntries(
                    Object.entries(overrides).filter(([k]) => k !== key),
                );
            } else {
                overrides = { ...overrides, [key]: value };
            }
        },
        reset() {
            overrides = {};
        },
        get hasOverrides() {
            return Object.keys(overrides).length > 0;
        },
        graphFilter() {
            const filter = {};
            for (const key of GRAPH_FILTER_KEYS) {
                filter[key] = this.get(key);
            }
            return filter;
        },
    };
}
