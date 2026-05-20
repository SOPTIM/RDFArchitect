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
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";

import { getPackageId } from "../packageNavigationUtils.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

async function getPackages(datasetName, graphURI) {
    const res = await bec.getPackages(datasetName, graphURI);
    return await res.json();
}

export async function createPackageListForGraph(datasetName, graphURI) {
    const res = await getPackages(datasetName, graphURI);

    return [...res.internalPackageList, ...res.externalPackageList]
        .map(pack => {
            const packageId = getPackageId(pack);

            return {
                uuid: packageId,
                prefix: pack.prefix,
                label: pack.label,
                selected: false,
                expanded: false,
            };
        })
        .sort((a, b) => {
            if (a.label === "default") return 1;
            if (b.label === "default") return -1;
            return a.label.localeCompare(b.label);
        });
}

async function getClasses(datasetName, graphURI) {
    const res = await bec.getClasses(datasetName, graphURI);
    return await res.json();
}

export async function createClassListForGraph(
    datasetName,
    graphURI,
    selectedClasses,
) {
    try {
        const classList = (await getClasses(datasetName, graphURI)) ?? [];

        const grouped = {};

        for (const cls of classList) {
            const packageId = getPackageId(cls.package);
            if (!grouped[packageId]) {
                grouped[packageId] = [];
            }

            cls.selected = !!selectedClasses.some(
                selected => selected.uuid === cls.uuid,
            );

            grouped[packageId].push({
                ...cls,
                packageUUID: packageId,
            });
        }

        for (const key of Object.keys(grouped)) {
            grouped[key].sort((a, b) =>
                (a.label ?? "").localeCompare(b.label ?? "", undefined, {
                    sensitivity: "base",
                }),
            );
        }

        return grouped;
    } catch (err) {
        console.error("Failed to load classes:", err);
    }
}
