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

import { MultiSelectState } from "./multiSelectState.svelte.js";
import {
    SimpleTrigger,
    StateObjectPair,
    StateValuePair,
} from "./statePrimitives.svelte.js";

export {
    mergeSelections,
    toggleSelections,
} from "./multiSelectState.svelte.js";

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
 * Navigation levels, least to most specific. `activeSelectionKind` holds one to
 * mark the last-picked level (recency the nested fields can't express alone).
 */
export const SelectionLevel = {
    DATASET: "dataset",
    GRAPH: "graph",
    PACKAGE: "package",
    DIAGRAM: "diagram",
    CLASS: "class",
    NONE: "none",
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
    // The level the user selected last; drives the nav highlight (see SelectionLevel).
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

    selectDataset(datasetName) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.DATASET);
        if (this.selectedDataset.getValue() === datasetName) {
            return;
        }
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.selectedDataset.updateValue(datasetName);
    },

    selectGraph(datasetName, graphUri) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.GRAPH);
        const graphChanged =
            this.selectedDataset.getValue() !== datasetName ||
            this.selectedGraph.getValue() !== graphUri;
        this.selectedDataset.updateValue(datasetName);
        this.selectedGraph.updateValue(graphUri);
        if (graphChanged) {
            this.selectedDiagram.updateValue({ type: null, id: null });
        }
    },

    selectPackage(datasetName, graphUri, packageId) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.PACKAGE);
        this.selectedDataset.updateValue(datasetName);
        this.selectedGraph.updateValue(graphUri);
        this.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: packageId,
        });
    },

    selectCustomDiagram(datasetName, graphUri, diagramId, diagramType) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.DIAGRAM);
        this.selectedDataset.updateValue(datasetName);
        this.selectedGraph.updateValue(graphUri ?? null);
        this.selectedDiagram.updateValue({ type: diagramType, id: diagramId });
    },

    markClassActive() {
        this.activeSelectionKind.updateValue(SelectionLevel.CLASS);
    },

    dissolveToDataset() {
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.activeSelectionKind.updateValue(SelectionLevel.DATASET);
    },

    dissolveToGraph() {
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.activeSelectionKind.updateValue(SelectionLevel.GRAPH);
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

    remove(datasetName, graphURI, classUUID) {
        this.entries.updateValue(
            this.getEntries().filter(
                e =>
                    e.classUUID !== classUUID ||
                    e.graphURI !== graphURI ||
                    e.datasetName !== datasetName,
            ),
        );
    },

    reset() {
        this.entries.updateValue([]);
    },
};

/**
 * Stores validation results to display on /validate.
 * @type {{ result: StateValuePair<any | null> }}
 */
export const validationState = {
    result: new StateValuePair(null),
};

export const multiSelectState = new MultiSelectState();

export const migrationState = writable({
    compareMode: null,
    datasetA: null,
    graphA: null,
    graphB: null,
    datasetB: null,
    fileA: null,
    fileB: null,
});
