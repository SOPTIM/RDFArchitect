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

import { editorState } from "$lib/sharedState.svelte.js";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

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

    getActive() {
        return editorState.selectedWorkspace.getValue();
    },

    /**
     * Reloads the list, keeping the active workspace if it still exists. A
     * failed request leaves the previous list untouched, so a backend outage
     * does not look like "no workspaces".
     */
    async load() {
        const workspaces = await workspaceStore.getWorkspaces();
        if (workspaces === null) {
            return;
        }

        for (const workspace of selectionByWorkspace.keys()) {
            if (!workspaces.some(ws => ws.label === workspace)) {
                selectionByWorkspace.delete(workspace);
            }
        }

        const active = this.getActive();
        if (!active || !workspaces.some(ws => ws.label === active)) {
            this.activate(workspaces[0] ?? null);
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
        const { error } = await workspaceStore.create(name);
        if (error) {
            return false;
        }
        selectionByWorkspace.delete(name);
        this.activate(name);
        return true;
    },

    async rename(name, newName) {
        const { error } = await workspaceStore.rename(name, newName);
        if (error) {
            return false;
        }
        if (selectionByWorkspace.has(name)) {
            selectionByWorkspace.set(newName, selectionByWorkspace.get(name));
            selectionByWorkspace.delete(name);
        }
        editorState.renameWorkspace(name, newName);
        return true;
    },

    async remove(name) {
        const { error } = await workspaceStore.remove(name);
        if (error) return false;

        selectionByWorkspace.delete(name);
        if (this.getActive() === name) {
            const workspaces = await workspaceStore.getWorkspaces();
            editorState.reset();
            this.activate(workspaces[0] ?? null);
        }
        return true;
    },
};
