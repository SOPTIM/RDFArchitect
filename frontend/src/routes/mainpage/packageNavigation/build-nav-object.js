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
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { URI } from "$lib/models/dto/index.ts";
import { NavEntry } from "$lib/models/nav/NavEntry.svelte.js";
import { DiagramType, editorState } from "$lib/sharedState.svelte.js";
import { getPackageDisplayLabel } from "$lib/utils/package-label.js";

import {
    getUri,
    isSelectedGraph,
    isSelectedPackage,
    isSelectedClass,
} from "./packageNavigationUtils.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

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

function syncList(targetArray, freshEntries, parent = null) {
    targetArray.length = 0;
    targetArray.push(...freshEntries);
    freshEntries.forEach(entry => (entry.parent = parent));
}

/**
 * Builds the navigation tree of a single workspace. Schemas are the top level;
 * the returned entry only carries them and is not rendered itself.
 */
export async function getWorkspaceNavEntry(workspaceName, existingNavEntry) {
    if (!workspaceName) {
        return null;
    }
    const workspaceNavEntry =
        existingNavEntry?.id === workspaceName
            ? existingNavEntry
            : new NavEntry({ label: workspaceName, id: workspaceName });

    try {
        await populateWorkspace(workspaceNavEntry);
    } catch (err) {
        console.error("Error populating workspace " + workspaceName, err);
        toastStore.error(
            "Failed to load workspace",
            `Could not load schemas for workspace "${workspaceName}".`,
        );
    }
    return workspaceNavEntry;
}

async function populateWorkspace(workspaceNavEntry) {
    const existingGraphNavList = workspaceNavEntry.children;

    const freshEntries = (await getGraphNames(workspaceNavEntry.id))
        .sort((a, b) => getUri(a).localeCompare(getUri(b)))
        .map(graph => {
            const fullUri = getUri(graph);
            return reuseOrCreate(existingGraphNavList, {
                label: graph.keyword ?? new URI(fullUri).suffix,
                tooltip: fullUri,
                id: fullUri,
            });
        });

    if (workspaceNavEntry.children) {
        syncList(workspaceNavEntry.children, freshEntries, workspaceNavEntry);
    } else {
        workspaceNavEntry.children = freshEntries;
        freshEntries.forEach(entry => (entry.parent = workspaceNavEntry));
    }

    for (const graphNavEntry of workspaceNavEntry.children) {
        if (isSelectedGraph(workspaceNavEntry.id, graphNavEntry.id)) {
            graphNavEntry.parent?.open();
        }
        await populateGraph(workspaceNavEntry, graphNavEntry);
    }
}

async function getGraphNames(workspaceName) {
    try {
        const res = await bec.getGraphs(workspaceName);
        if (!res.ok) {
            console.error(
                `Error fetching graph names for workspace "${workspaceName}": HTTP ${res.status}`,
            );
            return [];
        }
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching graph names for workspace " + workspaceName,
            err,
        );
        return [];
    }
}

export async function populateGraph(workspaceNavObject, graphNavObject) {
    const existingPackageList = graphNavObject.children;
    const packageApiObject = await getPackages(
        workspaceNavObject.id,
        graphNavObject.id,
    );
    const allClasses = await getClasses(
        workspaceNavObject.id,
        graphNavObject.id,
    );

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
                workspaceNavObject.id,
                graphNavObject.id,
                packageNavEntry.id,
            )
        ) {
            packageNavEntry.parent?.open();
        }
        populatePackage(
            packageNavEntry,
            allClasses,
            workspaceNavObject.id,
            graphNavObject.id,
        );
    }
    return graphNavObject;
}

/**
 * @description Reuses or creates a package NavEntry.
 * id is always the UUID (or "default" for the default package) for backend calls.
 * navKey is a unique string for Svelte's {#each} key.
 */
function reuseOrCreatePackage(existingPackageList, packObj, isExternal) {
    const label = packObj.label ?? "default";
    const id = packObj.uuid ?? "default";
    const navKey = (packObj.prefix ?? "") + label;
    const displayLabel = getPackageDisplayLabel(label);
    const entry = reuseOrCreate(existingPackageList, {
        id,
        tooltip: (packObj.prefix ?? "") + label,
        label: displayLabel,
        data: {
            uuid: packObj.uuid,
            prefix: packObj.prefix,
            label: label,
            comment: packObj.comment,
            external: isExternal,
            navKey,
        },
    });
    entry.navKey = navKey;
    return entry;
}

async function getPackages(workspaceName, graphURI) {
    try {
        const res = await bec.getPackages(workspaceName, graphURI);
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching packages for workspace " +
                workspaceName +
                " and graph " +
                graphURI,
            err,
        );
        return { internalPackageList: [], externalPackageList: [] };
    }
}

function populatePackage(packageNavObject, allClasses, workspaceId, graphId) {
    const existingClassList = packageNavObject.children;

    console.debug(
        "Populating package",
        packageNavObject.id,
        "with classes:",
        allClasses,
    );
    allClasses = allClasses ?? [];
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
        let diagramType = editorState.selectedDiagram.getProperty("type");
        if (
            diagramType === DiagramType.PACKAGE &&
            isSelectedClass(workspaceId, graphId, classNavEntry.id)
        ) {
            classNavEntry.parent?.open();
        }
    }

    return packageNavObject;
}

async function getClasses(workspaceName, graphURI) {
    try {
        const res = await bec.getClasses(workspaceName, graphURI);
        console.debug(
            "Fetched classes for workspace " +
                workspaceName +
                " and graph " +
                graphURI,
            res,
        );
        return await res.json();
    } catch (err) {
        console.error(
            "Error fetching classes for workspace " +
                workspaceName +
                " and graph " +
                graphURI,
            err,
        );
        return [];
    }
}
