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
 * What a selection in the import dialog adds up to.
 *
 * Everything shown beside the tree and everything sent to the import follows from three things:
 * what the read reported, which rows are ticked and which diagrams were turned down. Working them
 * out in one place keeps the dialog's buttons, its summary and its request saying the same thing —
 * and lets the request be built from the read it was made against rather than from whatever the
 * dialog is showing by then.
 */

import {
    chosenDiagramIds,
    diagramHomes,
    diagramsByPackage,
    groupsOf,
} from "$lib/utils/diagramSelection.svelte.js";
import { selectedSchemas, sizeOf } from "$lib/utils/packageTree.svelte.js";

/**
 * @param read the progress of the read, or null before one has finished
 * @param selectedRowIds the rows ticked in the tree
 * @param deselectedDiagramIds the diagrams ticked away
 * @returns {{schemas: object[], orderedRowIds: string[], groups: Map<string, string[]>,
 *     diagramsByOwner: Map<string, object[]>, homes: {byRow: Map<string, object[]>,
 *     workspace: object[]}, diagramIds: string[], classes: number}} the schemas the selection
 *     produces in the order they are imported, the diagrams that come with them and where each
 *     lands, and how many classes it all amounts to
 */
export function importChoiceOf(read, selectedRowIds, deselectedDiagramIds) {
    const diagrams = read?.diagrams ?? [];
    const groups = groupsOf(read?.supplierGroups ?? []);
    const schemas = selectedSchemas(read?.packages ?? [], selectedRowIds);
    const orderedRowIds = schemas.map(row => row.id);
    return {
        schemas,
        orderedRowIds,
        groups,
        diagramsByOwner: diagramsByPackage(diagrams),
        homes: diagramHomes(
            diagrams,
            groups,
            orderedRowIds,
            deselectedDiagramIds,
        ),
        diagramIds: chosenDiagramIds(
            diagrams,
            groups,
            orderedRowIds,
            deselectedDiagramIds,
        ),
        classes: schemas.reduce(
            (sum, row) => sum + sizeOf(row.node, row.scope),
            0,
        ),
    };
}
