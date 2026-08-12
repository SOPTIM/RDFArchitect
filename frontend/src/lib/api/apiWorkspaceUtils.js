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
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

export async function isReadOnly(workspaceName) {
    const res = await bec.isReadOnly(workspaceName);
    return await res.json();
}

export async function getNamespaces(workspaceName) {
    if (!workspaceName) {
        return [];
    }
    const res = await bec.getNamespaces(workspaceName);
    return await res.json();
}

export async function getWorkspaceNames() {
    const res = await bec.getWorkspaceNames();
    let workspaceNames = await res.json();
    let readOnlyWorkspaces = [];
    let modifiableWorkspaces = [];

    for (const workspace of workspaceNames) {
        if (await isReadOnly(workspace)) {
            readOnlyWorkspaces.push(workspace);
        } else {
            modifiableWorkspaces.push(workspace);
        }
    }
    return { modifiable: modifiableWorkspaces, readonly: readOnlyWorkspaces };
}

export async function getCrossProfileDiagram(workspaceName) {
    if (!workspaceName) return null;
    const res = await bec.getCrossProfileDiagramForWorkspace(workspaceName);
    return await res.json();
}
