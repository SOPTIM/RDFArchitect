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
    computeMappingType,
    describeAttributeChange,
    filterRenameCandidates,
    groupMappingsForSubmission,
    hasDataTypeChange,
    hasDefaultValue,
    isDefaultValueMissing,
    isNonInstantiableAssociationRename,
    isPrefixOnlyRename,
    keepsExistingValues,
    requiresDefaultValue,
    skipsInitialization,
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

    it.each(["MOVE", "SPLIT", "MERGE"])(
        "also asks for a default value when a %s result has a data type change",
        type => {
            expect(
                requiresDefaultValue(
                    dataTypeChanged({ semanticResourceChangeType: type }),
                ),
            ).toBe(true);
        },
    );
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

describe("waiving the default value", () => {
    const mandatory = (overrides = {}) =>
        attribute({
            semanticResourceChangeType: "ADD",
            optional: false,
            ...overrides,
        });

    it("blocks the step while the required default value is missing", () => {
        expect(isDefaultValueMissing(mandatory())).toBe(true);
    });

    it("stops blocking once a default value is given", () => {
        expect(isDefaultValueMissing(mandatory({ defaultValue: "0" }))).toBe(
            false,
        );
    });

    it("does not accept a blank default value", () => {
        expect(hasDefaultValue(mandatory({ defaultValue: "  " }))).toBe(false);
        expect(isDefaultValueMissing(mandatory({ defaultValue: "  " }))).toBe(
            true,
        );
    });

    it("stops blocking once the user waived the default value", () => {
        const waived = mandatory({ noDefaultValue: true });

        expect(skipsInitialization(waived)).toBe(true);
        expect(isDefaultValueMissing(waived)).toBe(false);
    });

    it("also applies to an optional attribute whose data type changed", () => {
        // the existing values have to be converted, so this one needs a default value too
        const converted = dataTypeChanged({ optional: true });

        expect(isDefaultValueMissing(converted)).toBe(true);
        expect(
            skipsInitialization({ ...converted, noDefaultValue: true }),
        ).toBe(true);
    });

    it("turns the waiver inert where no default value is asked for", () => {
        const optional = mandatory({ optional: true, noDefaultValue: true });
        const equivalent = dataTypeChanged({
            dataTypesEquivalent: true,
            noDefaultValue: true,
        });

        expect(skipsInitialization(optional)).toBe(false);
        expect(skipsInitialization(equivalent)).toBe(false);
        expect(isDefaultValueMissing(equivalent)).toBe(false);
    });
});

describe("isNonInstantiableAssociationRename", () => {
    const candidate = (associationUsed, newResource = null) => ({
        oldResources: [
            {
                iri: `${PREFIX}Switch.Terminals`,
                label: "Terminals",
                associationUsed,
            },
        ],
        newResources: newResource ? [newResource] : [],
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
            deletedAndRemapped: [
                {
                    oldResources: [association("Terminals", false)],
                    newResources: [association("ConnectedTerminals", false)],
                    confidenceScore: 0.9,
                },
                {
                    oldResources: [association("Windings", true)],
                    newResources: [],
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
            filtered.deletedAndRemapped.map(c => c.oldResources[0].label),
        ).toEqual(["Windings"]);
        // the target is linked, just not shown, so it must not resurface as unlinked
        expect(filtered.added.map(a => a.label)).toEqual(["Ends"]);
    });

    it("leaves the source overview untouched, so the hidden ones are still submitted", () => {
        const source = overview();

        filterRenameCandidates(source, {
            alsoHide: isNonInstantiableAssociationRename,
        });

        expect(source.deletedAndRemapped).toHaveLength(2);
        expect(source.added).toHaveLength(2);
    });

    it("applies the prefix-only rule only when asked to", () => {
        const properties = {
            added: [{ iri: "http://other#Switch.Terminals" }],
            deletedAndRemapped: [
                {
                    oldResources: [{ iri: `${PREFIX}Switch.Terminals` }],
                    newResources: [{ iri: "http://other#Switch.Terminals" }],
                },
            ],
        };

        expect(
            filterRenameCandidates(properties, { ignorePrefixes: true })
                .deletedAndRemapped,
        ).toHaveLength(0);
        expect(
            filterRenameCandidates(properties, { ignorePrefixes: false })
                .deletedAndRemapped,
        ).toHaveLength(1);
    });

    it("also filters the chain-wide allAdded pool, falling back to addedInClass", () => {
        const properties = {
            addedInClass: [association("ConnectedTerminals", false)],
            allAdded: [
                association("ConnectedTerminals", false),
                association("Ends", true),
            ],
            deletedAndRemapped: [
                {
                    oldResources: [association("Terminals", false)],
                    newResources: [association("ConnectedTerminals", false)],
                    confidenceScore: 0.9,
                },
            ],
        };

        const filtered = filterRenameCandidates(properties, {
            alsoHide: isNonInstantiableAssociationRename,
        });

        expect(filtered.addedInClass).toEqual([]);
        expect(filtered.allAdded.map(a => a.label)).toEqual(["Ends"]);
    });

    it("passes an absent overview through", () => {
        expect(filterRenameCandidates(undefined)).toBeUndefined();
    });
});

describe("computeMappingType", () => {
    const property = (label, domain) => ({
        iri: `${PREFIX}${domain}.${label}`,
        label,
        domain: `${PREFIX}${domain}`,
    });

    it("is RENAME for a 1:1 link within the same class", () => {
        expect(
            computeMappingType(
                [property("state", "Switch")],
                [property("status", "Switch")],
            ),
        ).toBe("RENAME");
    });

    it("is MOVE for a 1:1 link across classes", () => {
        expect(
            computeMappingType(
                [property("state", "Switch")],
                [property("state", "Breaker")],
            ),
        ).toBe("MOVE");
    });

    it("is RENAME for a class-to-class link, which has no domain", () => {
        expect(
            computeMappingType(
                [{ iri: `${PREFIX}Switch`, label: "Switch" }],
                [{ iri: `${PREFIX}Breaker`, label: "Breaker" }],
            ),
        ).toBe("RENAME");
    });

    it("is MERGE for more than one old resource", () => {
        expect(
            computeMappingType(
                [property("state", "Switch"), property("mode", "Breaker")],
                [property("status", "Switch")],
            ),
        ).toBe("MERGE");
    });

    it("is SPLIT for more than one new resource", () => {
        expect(
            computeMappingType(
                [property("state", "Switch")],
                [property("status", "Switch"), property("mode", "Breaker")],
            ),
        ).toBe("SPLIT");
    });

    it("is UNMAPPED when there is no linked target", () => {
        expect(computeMappingType([property("state", "Switch")], [])).toBe(
            "UNMAPPED",
        );
    });
});

describe("groupMappingsForSubmission", () => {
    const property = (label, domain) => ({
        iri: `${PREFIX}${domain}.${label}`,
        label,
        domain: `${PREFIX}${domain}`,
    });

    it("drops rows with no linked target", () => {
        const { mappings } = groupMappingsForSubmission([
            { id: "a", oldResources: [property("x", "A")], newResources: [] },
        ]);

        expect(mappings).toEqual([]);
    });

    it("keeps an untouched 1:1 candidate as-is, with its type recomputed", () => {
        const row = {
            id: "a",
            oldResources: [property("state", "Switch")],
            newResources: [property("state", "Breaker")],
            confidenceScore: 0.87,
        };

        const { mappings } = groupMappingsForSubmission([row]);

        expect(mappings).toHaveLength(1);
        expect(mappings[0].id).toBe("a");
        expect(mappings[0].type).toBe("MOVE");
    });

    it("groups two rows linked to the same target into one MERGE mapping", () => {
        const target = property("status", "Common");
        const rows = [
            {
                id: "a",
                oldResources: [property("state", "Switch")],
                newResources: [target],
            },
            {
                id: "b",
                oldResources: [property("mode", "Breaker")],
                newResources: [target],
            },
        ];

        const { mappings, mappingByRowId } = groupMappingsForSubmission(rows);

        expect(mappings).toHaveLength(1);
        expect(mappings[0].type).toBe("MERGE");
        expect(mappings[0].oldResources.map(r => r.label).sort()).toEqual([
            "mode",
            "state",
        ]);
        expect(mappingByRowId.get("a")).toBe(mappings[0]);
        expect(mappingByRowId.get("b")).toBe(mappings[0]);
    });

    it("groups one row linked to two targets into one SPLIT mapping", () => {
        const row = {
            id: "a",
            oldResources: [property("state", "Switch")],
            newResources: [
                property("open", "Switch"),
                property("closed", "Switch"),
            ],
        };

        const { mappings } = groupMappingsForSubmission([row]);

        expect(mappings).toHaveLength(1);
        expect(mappings[0].type).toBe("SPLIT");
        expect(mappings[0].newResources).toHaveLength(2);
    });

    it("rejects a group that ends up with more than one old and new resource", () => {
        const shared = property("shared", "Common");
        const rows = [
            {
                id: "a",
                oldResources: [property("state", "Switch")],
                newResources: [shared, property("extra", "Switch")],
            },
            {
                id: "b",
                oldResources: [property("mode", "Breaker")],
                newResources: [shared],
            },
        ];

        const { mappings, invalidMappingIds } =
            groupMappingsForSubmission(rows);

        expect(mappings).toEqual([]);
        expect(invalidMappingIds).toEqual(new Set(["a", "b"]));
    });
});
