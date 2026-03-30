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

import { getUri } from "./packageNavigationUtils.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

/**
 * Hier werden alle datasets geladen.
 * Falls bereits ein Dataset bereits existiert, werden beinhaltete flags übernommen, damit die Navigation im gleichen openstate bleibt
 * @param existingDatasetNavList
 * @returns {Promise<NavEntry[]>}
 */
export async function getNavEntryList(existingDatasetNavList) {
    console.log(
        "started Building navObj with existingNavObj: ",
        existingDatasetNavList,
    );

    const newDatasetNavList = (await getDatasetNames())
        .sort((a, b) => a.localeCompare(b))
        .map(label => new NavEntry({ label, id: label }));
    for (const newDatasetNavEntry of newDatasetNavList) {
        const existingDatasetNavEntry = existingDatasetNavList?.find(
            existingNavEntry => existingNavEntry.id === newDatasetNavEntry.id,
        );
        newDatasetNavEntry.isOpen = existingDatasetNavEntry?.isOpen ?? false;
        await populateDataset(
            newDatasetNavEntry,
            existingDatasetNavEntry?.children,
        );
    }
    console.log("finished Building navObj: ", newDatasetNavList);
    return newDatasetNavList;
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

async function populateDataset(datasetNavEntry, existingGraphNavList) {
    datasetNavEntry.children = (await getGraphNames(datasetNavEntry.id))
        .sort((a, b) => getUri(a).localeCompare(getUri(b))) // change this to sort by suffix if wanted
        .map(uri => {
            const fullUri = getUri(uri);
            const newUri = new URI(fullUri);
            const argumentObject = {
                label: newUri.suffix,
                tooltip: fullUri,
                id: fullUri,
            };
            return new NavEntry(argumentObject);
        });
    for (const graphNavEntry of datasetNavEntry.children) {
        const existingGraph = existingGraphNavList?.find(
            g => g.id === graphNavEntry.id,
        );
        graphNavEntry.isOpen = existingGraph?.isOpen ?? false;
        await populateGraph(
            datasetNavEntry,
            graphNavEntry,
            existingGraph?.children,
        );
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

export async function populateGraph(
    datasetNavObject,
    graphNavObject,
    existingPackageList,
) {
    const packageApiObject = await getPackages(
        datasetNavObject.id,
        graphNavObject.id,
    );
    const allClasses = await getClasses(datasetNavObject.id, graphNavObject.id);

    graphNavObject.children = packageApiObject.internalPackageList
        .map(pack => buildPackageNavEntry(pack, false))
        .concat(
            packageApiObject.externalPackageList.map(pack =>
                buildPackageNavEntry(pack, true),
            ),
        )
        .sort((a, b) => {
            if (!a || !a.label || a.label === "default") return 1;
            if (!b || !b.label || b.label === "default") return -1;
            return a.label.localeCompare(b.label);
        });

    for (const packageNavEntry of graphNavObject.children) {
        const prev = existingPackageList?.find(
            p => p.id === packageNavEntry.id,
        );
        packageNavEntry.isOpen = prev?.isOpen ?? false;
        populatePackage(packageNavEntry, allClasses);
    }
    return graphNavObject;
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
        return {
            internalPackageList: [],
            externalPackageList: [],
        };
    }
}

function buildPackageNavEntry(packObj, isExternal) {
    const dataObj = {
        uuid: packObj.uuid ? packObj.uuid : "default",
        prefix: packObj.prefix,
        label: packObj.label,
        comment: packObj.comment,
        external: isExternal,
    };
    return new NavEntry({
        id: packObj.uuid ? packObj.uuid : "default",
        tooltip: packObj.prefix + packObj.label,
        label: packObj.label,
        data: dataObj,
    });
}

function populatePackage(packageNavObject, allClasses) {
    packageNavObject.children = allClasses
        .filter(cls => packageNavObject.id === (cls.package?.uuid ?? "default"))
        .map(cls => buildClassNavEntry(cls))
        .sort((a, b) => a.label.localeCompare(b.label));

    return packageNavObject;
}

function buildClassNavEntry(cls) {
    return new NavEntry({
        id: cls.uuid,
        tooltip: cls.package?.prefix + cls.label,
        label: cls.label,
        data: {
            uuid: cls.uuid,
            prefix: cls.package?.prefix,
            label: cls.label,
            comment: cls.comment,
        },
    });
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
