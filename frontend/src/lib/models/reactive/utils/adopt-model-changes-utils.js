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
export function adoptUnsavedClassChanges(newClass, oldClass) {
    if (!oldClass || newClass.uuid.backup !== oldClass.uuid.backup) {
        return newClass;
    }

    adoptIfModified(newClass.uuid, oldClass.uuid);
    adoptIfModified(newClass.namespace, oldClass.namespace);
    adoptIfModified(newClass.label, oldClass.label);
    adoptIfModified(newClass.package, oldClass.package);
    adoptIfModified(newClass.superClass, oldClass.superClass);
    adoptIfModified(newClass.comment, oldClass.comment);

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
        adoptUnsavedAttributeChanges,
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
        adoptUnsavedEnumEntryChanges,
    );

    return newClass;
}

function adoptIfModified(target, source) {
    if (source.isModified) {
        target.value = source.value;
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
}

function adoptUnsavedAttributeChanges(newAttribute, oldAttribute) {
    adoptIfModified(newAttribute.uuid, oldAttribute.uuid);
    adoptIfModified(newAttribute.label, oldAttribute.label);
    adoptIfModified(newAttribute.namespace, oldAttribute.namespace);
    adoptIfModified(
        newAttribute.multiplicityLowerBound,
        oldAttribute.multiplicityLowerBound,
    );
    adoptIfModified(
        newAttribute.multiplicityUpperBound,
        oldAttribute.multiplicityUpperBound,
    );
    adoptIfModified(newAttribute.datatype, oldAttribute.datatype);
    adoptIfModified(newAttribute.comment, oldAttribute.comment);
    adoptIfModified(newAttribute.fixedValue, oldAttribute.fixedValue);
    adoptIfModified(newAttribute.defaultValue, oldAttribute.defaultValue);
}

function adoptUnsavedAssociationChanges(newAssociation, oldAssociation) {
    adoptIfModified(newAssociation.uuid, oldAssociation.uuid);
    adoptIfModified(newAssociation.label, oldAssociation.label);
    adoptIfModified(newAssociation.namespace, oldAssociation.namespace);
    adoptIfModified(newAssociation.domain, oldAssociation.domain);
    adoptIfModified(newAssociation.target, oldAssociation.target);
    adoptIfModified(
        newAssociation.multiplicityLowerBound,
        oldAssociation.multiplicityLowerBound,
    );
    adoptIfModified(
        newAssociation.multiplicityUpperBound,
        oldAssociation.multiplicityUpperBound,
    );
    adoptIfModified(newAssociation.comment, oldAssociation.comment);
    adoptIfModified(newAssociation.isUsed, oldAssociation.isUsed);
    // inverse
    adoptIfModified(newAssociation.inverse.uuid, oldAssociation.inverse.uuid);
    adoptIfModified(newAssociation.inverse.label, oldAssociation.inverse.label);
    adoptIfModified(
        newAssociation.inverse.namespace,
        oldAssociation.inverse.namespace,
    );
    adoptIfModified(
        newAssociation.inverse.multiplicityLowerBound,
        oldAssociation.inverse.multiplicityLowerBound,
    );
    adoptIfModified(
        newAssociation.inverse.multiplicityUpperBound,
        oldAssociation.inverse.multiplicityUpperBound,
    );
    adoptIfModified(
        newAssociation.inverse.comment,
        oldAssociation.inverse.comment,
    );
    adoptIfModified(
        newAssociation.inverse.isUsed,
        oldAssociation.inverse.isUsed,
    );
}

function adoptUnsavedEnumEntryChanges(newEnumEntry, oldEnumEntry) {
    adoptIfModified(newEnumEntry.uuid, oldEnumEntry.uuid);
    adoptIfModified(newEnumEntry.namespace, oldEnumEntry.namespace);
    adoptIfModified(newEnumEntry.label, oldEnumEntry.label);
    adoptIfModified(newEnumEntry.comment, oldEnumEntry.comment);
    adoptIfModified(newEnumEntry.stereotype, oldEnumEntry.stereotype);
}
