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

import { writable } from "svelte/store";

/**
 * StateValuePair allows us to create a state with a value and a trigger,
 * we can subscribe to the trigger or alternatively subscribe to only value changes.
 * Every time the value is updated, the trigger is toggled.
 * The trigger can also be toggled manually.
 */

import {
    SimpleTrigger,
    StateObjectPair,
    StateValuePair,
} from "./statePrimitives.svelte.js";

/**
 * Defines the possible values that the type property of selectedDiagam can have.
 * @type {{CUSTOM_DIAGRAM: string, PACKAGE: string}}
 */
export const DiagramType = {
    CUSTOM_GRAPH_DIAGRAM: "customGraphDiagram",
    CUSTOM_DATASET_DIAGRAM: "customDatasetDiagram",
    CROSS_PROFILE: "crossProfile",
    PACKAGE: "package",
};

export const ClassType = {
    SINGLE_CLASS: "singleClass",
    MERGED_CLASS: "mergedClass",
};

/**
 * The editorState object contains the state of the editor. Content might expand in the future.
 * @type {{
 *  selectedDataset: StateValuePair<string | null>,
 *  selectedGraph: StateValuePair<string | null>,
 *  selectedDiagram: StateObjectPair<Object | null>,
 *  selectedClassDataset: StateValuePair<string | null>,
 *  selectedClassGraph: StateValuePair<string | null>,
 *  selectedClass: StateObjectPair<Object | null>,
 *  focusedClassUUID: StateValuePair<string | null>,
 *  selectedContext: StateValuePair<string | null>,
 *  reset: () => void
 * }}
 */
export const editorState = {
    selectedDataset: new StateValuePair(),
    selectedGraph: new StateValuePair(),
    //can either be a package uuid or a custom diagram uuid
    selectedDiagram: new StateObjectPair({ type: null, id: null }),
    selectedClassDataset: new StateValuePair(),
    selectedClassGraph: new StateValuePair(),
    selectedClass: new StateObjectPair({ type: null, id: null }),
    focusedClassUUID: new StateValuePair(),
    selectedContext: new StateValuePair(),
    // The kind of resource the user selected last ("dataset" | "graph" |
    // "package" | "diagram" | "class"). Drives the nav highlight so the active
    // resource - not a still-selected class - shows as the most specific.
    activeSelectionKind: new StateValuePair(),

    reset() {
        this.selectedDataset.updateValue(null);
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.selectedClassDataset.updateValue(null);
        this.selectedClassGraph.updateValue(null);
        this.selectedClass.updateValue({ type: null, id: null });
        this.focusedClassUUID.updateValue(null);
        this.selectedContext.updateValue(null);
        this.activeSelectionKind.updateValue(null);
        multiSelectState.clear();
    },
};

/**
 * The graphViewState contains the states of variables relating to the view of a graph.
 * @type {{
 *  showGraphFilter: StateValuePair<boolean>,
 *  filter: StateValuePair<{
 *      includeEnumEntries: boolean,
 *      includeAttributes: boolean,
 *      includeAssociations: boolean,
 *      includeInheritance: boolean,
 *      includeRelationsToExternalPackages: boolean
 *  }>
 * }}
 */
export const graphViewState = {
    showGraphFilter: new StateValuePair(false),
    filter: new StateValuePair({
        includeEnumEntries: true,
        includeAttributes: true,
        includeAssociations: true,
        includeInheritance: true,
        includeRelationsToExternalPackages: true,
    }),
};

export const forceReloadTrigger = new SimpleTrigger();

/**
 * Stores compare results to display on /compare.
 * @type {{ changeList: StateValuePair<any[] | null> }}
 */
export const compareState = {
    changeList: new StateValuePair(null),
};

/**
 * Stores the classes that were copied and are available for paste.
 * Each entry is `{ classUUID, graphURI, datasetName }`. Supports copying multiple
 * classes at once (multiselect).
 * @type {{ entries: StateValuePair<Array<{classUUID: string, graphURI: string, datasetName: string}>> }}
 */
export const copyState = {
    entries: new StateValuePair([]),

    getEntries() {
        return this.entries.getValue() ?? [];
    },

    get isEmpty() {
        return this.getEntries().length === 0;
    },

    set(entries) {
        this.entries.updateValue([...entries]);
    },

    removeByUuid(classUUID) {
        this.entries.updateValue(
            this.getEntries().filter(e => e.classUUID !== classUUID),
        );
    },

    reset() {
        this.entries.updateValue([]);
    },
};

/**
 * Tracks the multi-selection of classes in the package navigation (Explorer-style:
 * Ctrl+Click toggles, Shift+Click selects a range). Each entry is
 * `{ datasetName, graphUri, classUuid, classLabel, packageId, classNavEntry }`.
 */
export const multiSelectState = {
    selectedClasses: new StateValuePair([]),
    /** The last single/ctrl-clicked entry, used as the anchor for Shift+Click ranges. */
    anchor: null,

    _key(entry) {
        return `${entry.datasetName}::${entry.graphUri}::${entry.classUuid}`;
    },

    getSelected() {
        return this.selectedClasses.getValue() ?? [];
    },

    isSelected(datasetName, graphUri, classUuid) {
        const key = `${datasetName}::${graphUri}::${classUuid}`;
        return this.getSelected().some(e => this._key(e) === key);
    },

    get count() {
        return this.getSelected().length;
    },

    get isMultiSelect() {
        return this.count > 1;
    },

    /** True when all selected classes share the same dataset and graph. */
    get isSingleGraph() {
        const list = this.getSelected();
        if (list.length === 0) {
            return true;
        }
        const first = list[0];
        return list.every(
            e =>
                e.datasetName === first.datasetName &&
                e.graphUri === first.graphUri,
        );
    },

    selectSingle(entry) {
        this.anchor = entry;
        this.selectedClasses.updateValue([entry]);
    },

    toggle(entry) {
        const key = this._key(entry);
        const list = this.getSelected();
        const exists = list.some(e => this._key(e) === key);
        const next = exists
            ? list.filter(e => this._key(e) !== key)
            : [...list, entry];
        this.anchor = entry;
        this.selectedClasses.updateValue(next);
    },

    /** Replaces the selection with the given contiguous range; keeps the anchor. */
    selectRange(rangeEntries) {
        this.selectedClasses.updateValue([...rangeEntries]);
    },

    clear() {
        this.anchor = null;
        this.selectedClasses.updateValue([]);
    },
};

export const migrationState = writable({
    compareMode: null,
    datasetA: null,
    graphA: null,
    graphB: null,
    datasetB: null,
    fileA: null,
    fileB: null,
});
