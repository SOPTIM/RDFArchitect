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

import { FocusField, PropertyKind } from "$lib/propertyEditorRequest.svelte.js";

import { labelsOf, ownerClassIdOf } from "../diagram/labelNodes.js";

/**
 * The association end an association edge is edited by, addressed like a property of the class
 * that owns it, the same way a double-click on one of the labels of the edge opens it. The end of
 * the source class is preferred.
 *
 * @returns the property target, or null if the edge carries no association end to open
 */
export function associationEditTarget(edge) {
    const labels = labelsOf(edge?.data).filter(
        label => label.associationEndUUID,
    );
    const label =
        labels.find(
            candidate => ownerClassIdOf(edge, candidate) === edge.source,
        ) ?? labels[0];
    if (!label) {
        return null;
    }
    return {
        classUuid: ownerClassIdOf(edge, label),
        graphUri: edge.data?.graphUri ?? null,
        kind: PropertyKind.ASSOCIATION,
        propertyUuid: label.associationEndUUID,
        focus: FocusField.LABEL,
    };
}

/** Whether an edge can be opened for editing: an inheritance or an association with an end. */
export function isEditableEdge(edge) {
    if (edge?.type === "inheritance") {
        return true;
    }
    return edge?.type === "association" && !!associationEditTarget(edge);
}
