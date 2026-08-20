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
    CUSTOM_WORKSPACE_DIAGRAM: "customWorkspaceDiagram",
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
    WORKSPACE: "workspace",
    GRAPH: "graph",
    PACKAGE: "package",
    DIAGRAM: "diagram",
    CLASS: "class",
    NONE: "none",
};

/**
 * The editorState object contains the state of the editor. Content might expand in the future.
 * @type {{
 *  selectedWorkspace: StateValuePair<string | null>,
 *  selectedGraph: StateValuePair<string | null>,
 *  selectedDiagram: StateObjectPair<Object | null>,
 *  selectedClassWorkspace: StateValuePair<string | null>,
 *  selectedClassGraph: StateValuePair<string | null>,
 *  selectedClass: StateObjectPair<Object | null>,
 *  focusedClassUUID: StateValuePair<string | null>,
 *  selectedContext: StateValuePair<string | null>,
 *  mergedViewOriginGraph: StateValuePair<string | null>,
 *  reset: () => void
 * }}
 */
export const editorState = {
    selectedWorkspace: new StateValuePair(),
    selectedGraph: new StateValuePair(),
    //can either be a package uuid or a custom diagram uuid
    selectedDiagram: new StateObjectPair({ type: null, id: null }),
    selectedClassWorkspace: new StateValuePair(),
    selectedClassGraph: new StateValuePair(),
    selectedClass: new StateObjectPair({ type: null, id: null }),
    focusedClassUUID: new StateValuePair(),
    selectedContext: new StateValuePair(),
    // The level the user selected last; drives the nav highlight (see SelectionLevel).
    activeSelectionKind: new StateValuePair(),
    // The graph the user came from when entering the merged view; preselects the
    // matching source in the merged class editor.
    mergedViewOriginGraph: new StateValuePair(),

    reset() {
        this.selectedWorkspace.updateValue(null);
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.selectedClassWorkspace.updateValue(null);
        this.selectedClassGraph.updateValue(null);
        this.selectedClass.updateValue({ type: null, id: null });
        this.focusedClassUUID.updateValue(null);
        this.selectedContext.updateValue(null);
        this.activeSelectionKind.updateValue(null);
        this.mergedViewOriginGraph.updateValue(null);
        multiSelectState.clear();
    },

    /** Snapshot of everything selected below the workspace level. */
    captureSelection() {
        return {
            graph: this.selectedGraph.getValue(),
            diagram: { ...(this.selectedDiagram.getValue() ?? {}) },
            classWorkspace: this.selectedClassWorkspace.getValue(),
            classGraph: this.selectedClassGraph.getValue(),
            selectedClass: { ...(this.selectedClass.getValue() ?? {}) },
            focusedClassUUID: this.focusedClassUUID.getValue(),
            context: this.selectedContext.getValue(),
            activeSelectionKind: this.activeSelectionKind.getValue(),
        };
    },

    /** Restores a snapshot taken by {@link captureSelection}. */
    applySelection(selection) {
        if (!selection) {
            return;
        }
        this.selectedGraph.updateValue(selection.graph ?? null);
        this.selectedDiagram.updateValue(
            selection.diagram ?? { type: null, id: null },
        );
        this.selectedClassWorkspace.updateValue(
            selection.classWorkspace ?? null,
        );
        this.selectedClassGraph.updateValue(selection.classGraph ?? null);
        this.selectedClass.updateValue(
            selection.selectedClass ?? { type: null, id: null },
        );
        this.focusedClassUUID.updateValue(selection.focusedClassUUID ?? null);
        this.selectedContext.updateValue(selection.context ?? null);
        this.activeSelectionKind.updateValue(
            selection.activeSelectionKind ?? null,
        );
    },

    /** Moves the selection onto a workspace that was renamed. */
    renameWorkspace(oldName, newName) {
        if (this.selectedWorkspace.getValue() === oldName) {
            this.selectedWorkspace.updateValue(newName);
        }
        if (this.selectedClassWorkspace.getValue() === oldName) {
            this.selectedClassWorkspace.updateValue(newName);
        }
    },

    /** Moves the selection onto a graph that was renamed. */
    renameGraph(workspaceName, oldGraphUri, newGraphUri) {
        if (
            this.selectedWorkspace.getValue() === workspaceName &&
            this.selectedGraph.getValue() === oldGraphUri
        ) {
            this.selectedGraph.updateValue(newGraphUri);
        }
        if (
            this.selectedClassWorkspace.getValue() === workspaceName &&
            this.selectedClassGraph.getValue() === oldGraphUri
        ) {
            this.selectedClassGraph.updateValue(newGraphUri);
        }
    },

    selectWorkspace(workspaceName) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.WORKSPACE);
        if (this.selectedWorkspace.getValue() === workspaceName) {
            return;
        }
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.selectedWorkspace.updateValue(workspaceName);
    },

    selectGraph(workspaceName, graphUri) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.GRAPH);
        const graphChanged =
            this.selectedWorkspace.getValue() !== workspaceName ||
            this.selectedGraph.getValue() !== graphUri;
        this.selectedWorkspace.updateValue(workspaceName);
        this.selectedGraph.updateValue(graphUri);
        if (graphChanged) {
            this.selectedDiagram.updateValue({ type: null, id: null });
        }
    },

    selectPackage(workspaceName, graphUri, packageId) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.PACKAGE);
        this.selectedWorkspace.updateValue(workspaceName);
        this.selectedGraph.updateValue(graphUri);
        this.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: packageId,
        });
    },

    selectCustomDiagram(workspaceName, graphUri, diagramId, diagramType) {
        multiSelectState.clear();
        this.activeSelectionKind.updateValue(SelectionLevel.DIAGRAM);
        this.selectedWorkspace.updateValue(workspaceName);
        this.selectedGraph.updateValue(graphUri ?? null);
        this.selectedDiagram.updateValue({ type: diagramType, id: diagramId });
    },

    markClassActive() {
        this.activeSelectionKind.updateValue(SelectionLevel.CLASS);
    },

    dissolveToWorkspace() {
        this.selectedGraph.updateValue(null);
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.activeSelectionKind.updateValue(SelectionLevel.WORKSPACE);
    },

    dissolveToGraph() {
        this.selectedDiagram.updateValue({ type: null, id: null });
        this.activeSelectionKind.updateValue(SelectionLevel.GRAPH);
    },
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
 * Each entry is `{ classUUID, graphURI, workspaceName }`. Supports copying multiple
 * classes at once (multiselect).
 * @type {{ entries: StateValuePair<Array<{classUUID: string, graphURI: string, workspaceName: string}>> }}
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

    remove(workspaceName, graphURI, classUUID) {
        this.entries.updateValue(
            this.getEntries().filter(
                e =>
                    e.classUUID !== classUUID ||
                    e.graphURI !== graphURI ||
                    e.workspaceName !== workspaceName,
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
    workspaceA: null,
    graphA: null,
    graphB: null,
    workspaceB: null,
    fileA: null,
    fileB: null,
    cgmesVersionA: null,
    cgmesVersionB: null,
});
