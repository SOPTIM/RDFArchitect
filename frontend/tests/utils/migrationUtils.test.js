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

import { describe, expect, it } from "vitest";

import {
    allowsDefaultValueInput,
    canKeepExistingValues,
    describeAttributeChange,
    filterRenameCandidates,
    hasDataTypeChange,
    isNonInstantiableAssociationRename,
    isPrefixOnlyRename,
    keepsExistingValues,
    requiresDefaultValue,
} from "../../src/lib/utils/migrationUtils.js";

const PREFIX = "http://example.org#";

function attribute(overrides = {}) {
    return {
        iri: `${PREFIX}Switch.state`,
        label: "state",
        semanticResourceChangeType: "CHANGE",
        optional: false,
        changes: [],
        ...overrides,
    };
}

function dataTypeChanged(overrides = {}) {
    return attribute({
        oldDataType: `${PREFIX}OldKind`,
        dataType: `${PREFIX}NewKind`,
        changes: [
            {
                semanticFieldChangeType: "DATATYPE_CHANGE",
                from: `${PREFIX}OldKind`,
                to: `${PREFIX}NewKind`,
            },
        ],
        ...overrides,
    });
}

function dataTypeRenamed(overrides = {}) {
    return attribute({
        dataType: `${PREFIX}NewKind`,
        changes: [
            {
                semanticFieldChangeType: "DATATYPE_RENAME",
                from: `${PREFIX}OldKind`,
                to: `${PREFIX}NewKind`,
            },
        ],
        ...overrides,
    });
}

describe("isPrefixOnlyRename", () => {
    it("recognises a rename that only moves the namespace", () => {
        expect(
            isPrefixOnlyRename(`${PREFIX}Switch`, "http://other#Switch"),
        ).toBe(true);
    });

    it("does not treat a different local name as prefix-only", () => {
        expect(isPrefixOnlyRename(`${PREFIX}Switch`, `${PREFIX}Breaker`)).toBe(
            false,
        );
    });

    it("is false when there is no target", () => {
        expect(isPrefixOnlyRename(`${PREFIX}Switch`, undefined)).toBe(false);
    });
});

describe("a renamed data type", () => {
    it("is not reported as a data type change", () => {
        expect(hasDataTypeChange(dataTypeRenamed())).toBe(false);
    });

    it("does not ask for a default value", () => {
        expect(requiresDefaultValue(dataTypeRenamed())).toBe(false);
    });

    it("is not offered a default value input at all", () => {
        expect(allowsDefaultValueInput(dataTypeRenamed())).toBe(false);
    });
});

describe("a changed data type", () => {
    it("asks for a default value", () => {
        expect(requiresDefaultValue(dataTypeChanged())).toBe(true);
        expect(describeAttributeChange(dataTypeChanged())).toBe(
            "Data type changed",
        );
    });

    it("keeps existing values once marked equivalent", () => {
        const marked = dataTypeChanged({ dataTypesEquivalent: true });

        expect(keepsExistingValues(marked)).toBe(true);
        expect(requiresDefaultValue(marked)).toBe(false);
        expect(describeAttributeChange(marked)).toBe("Data type equivalent");
    });

    it("stays visible in the step when marked equivalent, so it can be undone", () => {
        expect(
            allowsDefaultValueInput(
                dataTypeChanged({ dataTypesEquivalent: true }),
            ),
        ).toBe(true);
    });

    it("ignores the equivalence flag when nothing about the data type changed", () => {
        const madeRequired = attribute({
            dataTypesEquivalent: true,
            changes: [{ semanticFieldChangeType: "MADE_REQUIRED" }],
        });

        expect(keepsExistingValues(madeRequired)).toBe(false);
        expect(requiresDefaultValue(madeRequired)).toBe(true);
    });
});

describe("requiresDefaultValue", () => {
    it("requires one for a new mandatory attribute", () => {
        expect(
            requiresDefaultValue(
                attribute({ semanticResourceChangeType: "ADD" }),
            ),
        ).toBe(true);
    });

    it("does not require one for a new optional attribute", () => {
        expect(
            requiresDefaultValue(
                attribute({
                    semanticResourceChangeType: "ADD",
                    optional: true,
                }),
            ),
        ).toBe(false);
    });

    it("requires one for a newly inherited mandatory attribute", () => {
        expect(
            requiresDefaultValue(
                attribute({
                    semanticResourceChangeType: "ADDED_FROM_INHERITANCE",
                }),
            ),
        ).toBe(true);
    });

    it("does not require one for a deleted attribute", () => {
        expect(
            requiresDefaultValue(
                attribute({ semanticResourceChangeType: "DELETE" }),
            ),
        ).toBe(false);
    });
});

describe("the equivalence option", () => {
    it("is offered for a datatype change on an existing attribute", () => {
        expect(canKeepExistingValues(dataTypeChanged())).toBe(true);
    });

    it("is not offered for a newly added attribute", () => {
        // an added attribute also carries a DATATYPE_CHANGE, but from nothing
        const added = attribute({
            semanticResourceChangeType: "ADD",
            dataType: `${PREFIX}NewKind`,
            changes: [
                {
                    semanticFieldChangeType: "DATATYPE_CHANGE",
                    from: null,
                    to: `${PREFIX}NewKind`,
                },
            ],
        });

        expect(canKeepExistingValues(added)).toBe(false);
        expect(
            keepsExistingValues({ ...added, dataTypesEquivalent: true }),
        ).toBe(false);
        expect(
            requiresDefaultValue({ ...added, dataTypesEquivalent: true }),
        ).toBe(true);
    });

    it("does not excuse the default value of an attribute that also became mandatory", () => {
        const alsoMandatory = dataTypeChanged({
            dataTypesEquivalent: true,
            changes: [
                {
                    semanticFieldChangeType: "DATATYPE_CHANGE",
                    from: `${PREFIX}OldKind`,
                    to: `${PREFIX}NewKind`,
                },
                { semanticFieldChangeType: "MADE_REQUIRED" },
            ],
        });

        expect(keepsExistingValues(alsoMandatory)).toBe(true);
        expect(requiresDefaultValue(alsoMandatory)).toBe(true);
    });
});

describe("isNonInstantiableAssociationRename", () => {
    const candidate = (associationUsed, newResource = null) => ({
        oldResource: {
            iri: `${PREFIX}Switch.Terminals`,
            label: "Terminals",
            associationUsed,
        },
        newResource,
        confidenceScore: 0.9,
    });

    it("skips an association that cannot be instantiated", () => {
        expect(isNonInstantiableAssociationRename(candidate(false))).toBe(true);
    });

    it("still asks about an association that can be instantiated", () => {
        expect(isNonInstantiableAssociationRename(candidate(true))).toBe(false);
    });

    it("judges the old side, which is where the values would come from", () => {
        const usedOldSide = candidate(true, {
            iri: `${PREFIX}Switch.ConnectedTerminals`,
            label: "ConnectedTerminals",
            associationUsed: false,
        });

        expect(isNonInstantiableAssociationRename(usedOldSide)).toBe(false);
    });

    it("asks when the flag is missing rather than hiding the row", () => {
        expect(isNonInstantiableAssociationRename(candidate(undefined))).toBe(
            false,
        );
        expect(isNonInstantiableAssociationRename({})).toBe(false);
        expect(isNonInstantiableAssociationRename(undefined)).toBe(false);
    });
});

describe("filterRenameCandidates", () => {
    const association = (name, associationUsed) => ({
        iri: `${PREFIX}Switch.${name}`,
        label: name,
        associationUsed,
    });

    function overview() {
        return {
            added: [
                association("ConnectedTerminals", false),
                association("Ends", true),
            ],
            deletedAndRenamed: [
                {
                    oldResource: association("Terminals", false),
                    newResource: association("ConnectedTerminals", false),
                    confidenceScore: 0.9,
                },
                {
                    oldResource: association("Windings", true),
                    newResource: null,
                    confidenceScore: 0,
                },
            ],
        };
    }

    it("hides a non-instantiable association and its detected target", () => {
        const filtered = filterRenameCandidates(overview(), {
            alsoHide: isNonInstantiableAssociationRename,
        });

        expect(
            filtered.deletedAndRenamed.map(c => c.oldResource.label),
        ).toEqual(["Windings"]);
        // the target is linked, just not shown, so it must not resurface as unlinked
        expect(filtered.added.map(a => a.label)).toEqual(["Ends"]);
    });

    it("leaves the source overview untouched, so the hidden ones are still submitted", () => {
        const source = overview();

        filterRenameCandidates(source, {
            alsoHide: isNonInstantiableAssociationRename,
        });

        expect(source.deletedAndRenamed).toHaveLength(2);
        expect(source.added).toHaveLength(2);
    });

    it("applies the prefix-only rule only when asked to", () => {
        const properties = {
            added: [{ iri: "http://other#Switch.Terminals" }],
            deletedAndRenamed: [
                {
                    oldResource: { iri: `${PREFIX}Switch.Terminals` },
                    newResource: { iri: "http://other#Switch.Terminals" },
                },
            ],
        };

        expect(
            filterRenameCandidates(properties, { ignorePrefixes: true })
                .deletedAndRenamed,
        ).toHaveLength(0);
        expect(
            filterRenameCandidates(properties, { ignorePrefixes: false })
                .deletedAndRenamed,
        ).toHaveLength(1);
    });

    it("passes an absent overview through", () => {
        expect(filterRenameCandidates(undefined)).toBeUndefined();
    });
});
