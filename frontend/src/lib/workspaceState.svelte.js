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
import { editorState } from "$lib/sharedState.svelte.js";
import { StateValuePair } from "$lib/statePrimitives.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

/** @type {Map<string, Object>} selection to restore per workspace name */
const selectionByWorkspace = new Map();

/**
 * The workspaces of the current session. A workspace is the top level of the
 * navigation and holds schemas, packages and diagrams; on the wire it is still
 * called a dataset. The active workspace is `editorState.selectedWorkspace`.
 *
 * Everything a single workspace knows about itself (read-only state, number of
 * schemas) is loaded where it is shown, see `asyncValue`.
 */
export const workspaceState = {
    /** @type {StateValuePair<string[] | null>} null until the first load */
    names: new StateValuePair(null),

    getNames() {
        return this.names.getValue() ?? [];
    },

    isLoaded() {
        return this.names.getValue() !== null;
    },

    getActive() {
        return editorState.selectedWorkspace.getValue();
    },

    /**
     * Reloads the list, keeping the active workspace if it still exists. A
     * failed request leaves the previous list untouched, so a backend outage
     * does not look like "no workspaces".
     */
    async load() {
        const names = await fetchWorkspaceNames();
        if (names === null) {
            return;
        }
        this.names.updateValue(names);

        for (const name of [...selectionByWorkspace.keys()]) {
            if (!names.includes(name)) {
                selectionByWorkspace.delete(name);
            }
        }

        const active = this.getActive();
        if (!active || !names.includes(active)) {
            this.activate(names[0] ?? null);
        }
    },

    /** Activates a workspace and restores the selection it was left with. */
    activate(name) {
        const active = this.getActive();
        if (active === name) {
            return;
        }
        if (active) {
            selectionByWorkspace.set(active, editorState.captureSelection());
        }
        editorState.reset();
        editorState.selectWorkspace(name);
        if (name && selectionByWorkspace.has(name)) {
            editorState.applySelection(selectionByWorkspace.get(name));
        }
    },

    async create(name) {
        const res = await bec.createWorkspace(name);
        if (!res.ok) {
            toastStore.error(
                "Create failed",
                `Could not create workspace "${name}".`,
            );
            return false;
        }
        selectionByWorkspace.delete(name);
        this.names.updateValue(
            [...new Set([...this.getNames(), name])].sort((a, b) =>
                a.localeCompare(b),
            ),
        );
        this.activate(name);
        return true;
    },

    async remove(name) {
        const res = await bec.deleteWorkspace(name);
        if (res && res.ok === false) {
            toastStore.error(
                "Delete failed",
                `Could not delete workspace "${name}".`,
            );
            return false;
        }
        selectionByWorkspace.delete(name);
        const remaining = this.getNames().filter(
            workspaceName => workspaceName !== name,
        );
        this.names.updateValue(remaining);
        if (this.getActive() === name) {
            editorState.reset();
            this.activate(remaining[0] ?? null);
        }
        return true;
    },
};

/** @returns {Promise<string[] | null>} null when the request failed */
async function fetchWorkspaceNames() {
    try {
        const res = await bec.getWorkspaceNames();
        if (!res.ok) {
            console.error(`Error fetching workspaces: HTTP ${res.status}`);
            return null;
        }
        const names = await res.json();
        return names.sort((a, b) => a.localeCompare(b));
    } catch (err) {
        console.error("Error fetching workspaces", err);
        return null;
    }
}
