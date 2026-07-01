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

import { StateValuePair } from "./statePrimitives.svelte.js";

/** Stable identity key for a selection entry (dataset + graph + class uuid). */
function entryKey(entry) {
    return `${entry.datasetName}::${entry.graphUri}::${entry.classUuid}`;
}

/** Unions two selection-entry lists, keeping order and de-duplicating by key. */
export function mergeSelections(base, additions) {
    const seen = new Set(base.map(entryKey));
    const merged = [...base];
    for (const entry of additions) {
        const key = entryKey(entry);
        if (!seen.has(key)) {
            seen.add(key);
            merged.push(entry);
        }
    }
    return merged;
}

export function toggleSelections(base, toggled) {
    const toggledKeys = new Set(toggled.map(entryKey));
    const baseKeys = new Set(base.map(entryKey));
    const kept = base.filter(entry => !toggledKeys.has(entryKey(entry)));
    const added = toggled.filter(entry => !baseKeys.has(entryKey(entry)));
    return [...kept, ...added];
}

/**
 * Tracks the multi-selection of classes, both in the package navigation and in
 * the rendered diagrams
 */
export class MultiSelectState {
    selectedClasses = new StateValuePair([]);
    anchor = null;

    #keySource = null;
    #keySet = new Set();

    getSelected() {
        return this.selectedClasses.getValue() ?? [];
    }

    #selectedKeys() {
        const list = this.getSelected();
        if (this.#keySource !== list) {
            this.#keySource = list;
            this.#keySet = new Set(list.map(entryKey));
        }
        return this.#keySet;
    }

    subscribe() {
        return this.selectedClasses.subscribe();
    }

    isSelected(datasetName, graphUri, classUuid) {
        const key = entryKey({ datasetName, graphUri, classUuid });
        return this.#selectedKeys().has(key);
    }

    /**
     * True when `entries` is the same set (by key) as the current selection.
     */
    equals(entries) {
        const current = this.getSelected();
        if (current.length !== entries.length) {
            return false;
        }
        const keys = new Set(current.map(entryKey));
        return entries.every(e => keys.has(entryKey(e)));
    }

    get count() {
        return this.getSelected().length;
    }

    get isMultiSelect() {
        return this.count > 1;
    }

    /**
     * True when all selected classes share the same dataset and graph.
     */
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
    }

    /**
     * The selection mapped to copyState's `{ classUUID, graphURI, datasetName }` shape.
     */
    toCopyEntries() {
        return this.getSelected().map(e => ({
            classUUID: e.classUuid,
            graphURI: e.graphUri,
            datasetName: e.datasetName,
        }));
    }

    /**
     * Replaces the selection, but only when it differs from the current one
     */
    setSelection(entries) {
        if (this.equals(entries)) {
            return;
        }
        this.selectedClasses.updateValue([...entries]);
    }

    toggle(entry) {
        const key = entryKey(entry);
        const list = this.getSelected();
        const exists = list.some(e => entryKey(e) === key);
        const next = exists
            ? list.filter(e => entryKey(e) !== key)
            : [...list, entry];
        this.anchor = entry;
        this.selectedClasses.updateValue(next);
    }

    /**
     * Replaces the selection with the given contiguous range. keeps the anchor.
     */
    selectRange(rangeEntries) {
        this.selectedClasses.updateValue([...rangeEntries]);
    }

    clear() {
        this.anchor = null;
        this.selectedClasses.updateValue([]);
    }
}
