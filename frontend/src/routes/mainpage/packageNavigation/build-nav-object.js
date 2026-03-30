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
import { URI } from "$lib/models/dto/index.ts";
import { NavEntry } from "$lib/models/nav/NavEntry.svelte.js";

import {
    getUri,
    isSelectedGraph,
    isSelectedPackage,
    isSelectedClass,
} from "./packageNavigationUtils.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

/**
 * @description Reuses an existing NavEntry by id or creates a new one. Preserves isOpen.
 * @param {NavEntry[]} existingList
 * @param {object} props
 * @returns {NavEntry}
 */
function reuseOrCreate(existingList, props) {
    const existing = existingList?.find(e => e.id === props.id);
    if (existing) {
        existing.label = props.label;
        if (props.tooltip !== undefined) existing.tooltip = props.tooltip;
        if (props.data !== undefined) existing.data = props.data;
        return existing;
    }
    return new NavEntry(props);
}

/**
 * @description Replaces the contents of targetArray in place, keeping the array reference. Sets parent on each entry.
 * @param {NavEntry[]} targetArray
 * @param {NavEntry[]} freshEntries
 * @param {NavEntry|null} parent
 */
function syncList(targetArray, freshEntries, parent = null) {
    targetArray.length = 0;
    targetArray.push(...freshEntries);
    freshEntries.forEach(entry => (entry.parent = parent));
}

/**
 * @description Loads all datasets and populates their children. Reuses existing NavEntries to preserve UI state.
 * @param {NavEntry[]} existingDatasetNavList
 * @returns {Promise<NavEntry[]>}
 */
export async function getNavEntryList(existingDatasetNavList) {
    console.log(
        "started Building navObj with existingNavObj: ",
        existingDatasetNavList,
    );

    const freshEntries = (await getDatasetNames())
        .sort((a, b) => a.localeCompare(b))
        .map(label =>
            reuseOrCreate(existingDatasetNavList, { label, id: label }),
        );

    const result = existingDatasetNavList ?? [];
    syncList(result, freshEntries, null);

    for (const datasetNavEntry of result) {
        await populateDataset(datasetNavEntry);
    }

    console.log("finished Building navObj: ", result);
    return result;
}

async function getDatasetNames() {
    try {
        const res = await bec.getDatasetNames();
        return await res.json();
    } catch (err) {
        console.error("Error fetching dataset names", err);
        return [];
    }
}

async function populateDataset(datasetNavEntry) {
    const existingGraphNavList = datasetNavEntry.children;

    const freshEntries = (await getGraphNames(datasetNavEntry.id))
        .sort((a, b) => getUri(a).localeCompare(getUri(b)))
        .map(uri => {
            const fullUri = getUri(uri);
            return reuseOrCreate(existingGraphNavList, {
                label: new URI(fullUri).suffix,
                tooltip: fullUri,
                id: fullUri,
            });
        });

    if (datasetNavEntry.children) {
        syncList(datasetNavEntry.children, freshEntries, datasetNavEntry);
    } else {
        datasetNavEntry.children = freshEntries;
        freshEntries.forEach(entry => (entry.parent = datasetNavEntry));
    }

    for (const graphNavEntry of datasetNavEntry.children) {
        if (isSelectedGraph(datasetNavEntry.id, graphNavEntry.id)) {
            graphNavEntry.parent?.open();
        }
        await populateGraph(datasetNavEntry, graphNavEntry);
    }
}

async function getGraphNames(datasetName) {
    try {
        const res = await bec.getGraphNames(datasetName);
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching graph names for dataset " + datasetName,
            err,
        );
        return [];
    }
}

export async function populateGraph(datasetNavObject, graphNavObject) {
    const existingPackageList = graphNavObject.children;
    const packageApiObject = await getPackages(
        datasetNavObject.id,
        graphNavObject.id,
    );
    const allClasses = await getClasses(datasetNavObject.id, graphNavObject.id);

    const freshEntries = [
        ...packageApiObject.internalPackageList.map(pack =>
            reuseOrCreatePackage(existingPackageList, pack, false),
        ),
        ...packageApiObject.externalPackageList.map(pack =>
            reuseOrCreatePackage(existingPackageList, pack, true),
        ),
    ].sort((a, b) => {
        if (!a?.label || a.label === "default") return 1;
        if (!b?.label || b.label === "default") return -1;
        return a.label.localeCompare(b.label);
    });

    if (graphNavObject.children) {
        syncList(graphNavObject.children, freshEntries, graphNavObject);
    } else {
        graphNavObject.children = freshEntries;
        freshEntries.forEach(entry => (entry.parent = graphNavObject));
    }

    for (const packageNavEntry of graphNavObject.children) {
        if (
            isSelectedPackage(
                datasetNavObject.id,
                graphNavObject.id,
                packageNavEntry.id,
            )
        ) {
            packageNavEntry.parent?.open();
        }
        populatePackage(
            packageNavEntry,
            allClasses,
            datasetNavObject.id,
            graphNavObject.id,
        );
    }
    return graphNavObject;
}

/**
 * @description Reuses or creates a package NavEntry.
 * @param {NavEntry[]} existingPackageList
 * @param {object} packObj
 * @param {boolean} isExternal
 * @returns {NavEntry}
 */
function reuseOrCreatePackage(existingPackageList, packObj, isExternal) {
    const id = packObj.uuid ?? "default";
    return reuseOrCreate(existingPackageList, {
        id,
        tooltip: packObj.prefix + packObj.label,
        label: packObj.label,
        data: {
            uuid: id,
            prefix: packObj.prefix,
            label: packObj.label,
            comment: packObj.comment,
            external: isExternal,
        },
    });
}

async function getPackages(datasetName, graphURI) {
    try {
        const res = await bec.getPackages(datasetName, graphURI);
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching packages for dataset " +
                datasetName +
                " and graph " +
                graphURI,
            err,
        );
        return { internalPackageList: [], externalPackageList: [] };
    }
}

function populatePackage(packageNavObject, allClasses, datasetId, graphId) {
    const existingClassList = packageNavObject.children;

    const freshEntries = allClasses
        .filter(cls => packageNavObject.id === (cls.package?.uuid ?? "default"))
        .sort((a, b) => a.label.localeCompare(b.label))
        .map(cls =>
            reuseOrCreate(existingClassList, {
                id: cls.uuid,
                tooltip: cls.package?.prefix + cls.label,
                label: cls.label,
                data: {
                    uuid: cls.uuid,
                    prefix: cls.package?.prefix,
                    label: cls.label,
                    comment: cls.comment,
                },
            }),
        );

    if (packageNavObject.children) {
        syncList(packageNavObject.children, freshEntries, packageNavObject);
    } else {
        packageNavObject.children = freshEntries;
        freshEntries.forEach(entry => (entry.parent = packageNavObject));
    }

    for (const classNavEntry of packageNavObject.children) {
        if (isSelectedClass(datasetId, graphId, classNavEntry.id)) {
            classNavEntry.parent?.open();
        }
    }

    return packageNavObject;
}

async function getClasses(datasetName, graphURI) {
    try {
        const res = await bec.getClasses(datasetName, graphURI);
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching classes for dataset " +
                datasetName +
                " and graph " +
                graphURI,
            err,
        );
        return [];
    }
}
