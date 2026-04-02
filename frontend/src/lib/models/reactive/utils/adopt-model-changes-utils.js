/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";

export function adoptUnsavedClassChanges(newClass, oldClass) {
    if (!oldClass || newClass.uuid.backup !== oldClass.uuid.backup) {
        return newClass;
    }

    adoptModifiedProperties(newClass, oldClass);

    adoptModifiedArrayEntries(
        newClass.stereotypes,
        oldClass.stereotypes,
        (a, b) => a.backup === b.backup,
        (newEntry, oldEntry) => {
            newEntry.value = oldEntry.value;
        },
    );

    adoptModifiedArrayEntries(
        newClass.attributes,
        oldClass.attributes,
        (a, b) => a.uuid.backup === b.uuid.backup,
        adoptModifiedProperties,
    );

    adoptModifiedArrayEntries(
        newClass.associations,
        oldClass.associations,
        (a, b) => a.uuid.backup === b.uuid.backup,
        adoptUnsavedAssociationChanges,
    );

    adoptModifiedArrayEntries(
        newClass.enumEntries,
        oldClass.enumEntries,
        (a, b) => a.uuid.backup === b.uuid.backup,
        adoptModifiedProperties,
    );

    return newClass;
}

function adoptIfModified(target, source) {
    if (source.isModified) {
        target.value = source.value;
    }
}

function adoptModifiedProperties(target, source) {
    for (const key of Object.keys(source)) {
        if (source[key] instanceof ReactiveValueWrapper) {
            adoptIfModified(target[key], source[key]);
        }
    }
}

function adoptModifiedArrayEntries(newArray, oldArray, matchFn, adoptFn) {
    if (!oldArray.isModified) {
        return;
    }
    for (const oldEntry of oldArray.values) {
        const newEntry = newArray.values.find(e => matchFn(e, oldEntry));
        if (newEntry) {
            adoptFn(newEntry, oldEntry);
        } else {
            newArray.append(oldEntry);
        }
    }

    // Remove deleted entries
    for (const backupEntry of oldArray.backup) {
        const wasDeleted = !oldArray.values.some(e => matchFn(e, backupEntry));
        if (wasDeleted) {
            const entryInNewArray = newArray.values.find(e =>
                matchFn(e, backupEntry),
            );
            if (entryInNewArray) {
                newArray.remove(entryInNewArray, true);
            }
        }
    }
}

function adoptUnsavedAssociationChanges(newAssociation, oldAssociation) {
    adoptModifiedProperties(newAssociation, oldAssociation);
    adoptModifiedProperties(newAssociation.inverse, oldAssociation.inverse);
}
