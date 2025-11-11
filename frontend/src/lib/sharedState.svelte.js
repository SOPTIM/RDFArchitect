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

/**
 * StateValuePair allows us to create a state with a value and a trigger,
 * we can subscribe to the trigger or alternatively subscribe to only value changes.
 * Every time the value is updated, the trigger is toggled.
 * The trigger can also be toggled manually.
 */

import { SimpleTrigger, StateValuePair } from "./statePrimitives.svelte.js";

/**
 * The editorState object contains the state of the editor. Content might expand in the future.
 * @type {{
 *  selectedDataset: StateValuePair<string | null>,
 *  selectedGraph: StateValuePair<string | null>,
 *  selectedPackageUUID: StateValuePair<string | null>,
 *  selectedClassDataset: StateValuePair<string | null>,
 *  selectedClassGraph: StateValuePair<string | null>,
 *  selectedClassUUID: StateValuePair<string | null>,
 *  reset: () => void
 * }}
 */
export const editorState = {
    selectedDataset: new StateValuePair(),
    selectedGraph: new StateValuePair(),
    selectedPackageUUID: new StateValuePair(),
    selectedClassDataset: new StateValuePair(),
    selectedClassGraph: new StateValuePair(),
    selectedClassUUID: new StateValuePair(),

    reset() {
        this.selectedDataset.updateValue(null);
        this.selectedGraph.updateValue(null);
        this.selectedPackageUUID.updateValue(null);
        this.selectedClassDataset.updateValue(null);
        this.selectedClassGraph.updateValue(null);
        this.selectedClassUUID.updateValue(null);
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
