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

import { beforeEach, describe, expect, test, vi } from "vitest";

import { URI } from "$lib/models/dto/index.ts";

vi.mock("$lib/api/generated", () => ({
    listDatasets: vi.fn(),
    listGraphs: vi.fn(),
    resolveIri: vi.fn(),
    getClassInformation: vi.fn(),
    search: vi.fn(),
}));

import {
    getClassInformation,
    listDatasets,
    listGraphs,
    resolveIri,
    search,
} from "$lib/api/generated";
import { resolveClassTarget, resolveTermTarget } from "$lib/utils/deep-link.js";

const CLASS_IRI = "https://cim.example.org/CIM#ACLineSegment";
const CLASS_UUID = "8f7c2d7e-3f7a-4e1c-9a5e-2b6c1d0e9f4a";
const PACKAGE_UUID = "4a1b9c8d-7e6f-4a2b-8c3d-5e4f6a7b8c9d";
const ATTRIBUTE_IRI = "https://cim.example.org/CIM#ACLineSegment.r";
const ATTRIBUTE_UUID = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

const singleGraphModel = {
    profiles: {
        "https://cim.example.org/EQ": {
            [CLASS_IRI]: {
                uuid: CLASS_UUID,
                package: { uuid: PACKAGE_UUID, label: "Wires" },
            },
        },
    },
};

/**
 * Wires the mocked `$lib/api/generated` functions to a {dataset: {graphUri: {iri: classInfo}}}
 * fixture.
 *
 * Classes are looked up by IRI (resolveIri) or by their fixture uuid (getClassInformation). A
 * class may declare `attributes`, which resolve like any other resource but are not classes — the
 * backend answers with an empty body for those, as it does for a deleted class; the generated
 * client surfaces that as `data: undefined` with no error.
 */
function mockApiFor(model, { searchResults = [] } = {}) {
    const resourcesOf = graph =>
        Object.entries(graph ?? {}).flatMap(([iri, info]) => [
            [iri, info.uuid, info.deleted ? null : info],
            ...(info.attributes ?? []).map(a => [a.iri, a.uuid, null]),
        ]);

    listDatasets.mockResolvedValue({
        data: Object.keys(model).map(name => ({ name })),
    });

    listGraphs.mockImplementation(async ({ path: { datasetName } }) => {
        const graphs = model[datasetName];
        // The real endpoint serves URIs as plain {prefix, suffix} objects, never as strings.
        return {
            data: graphs
                ? Object.keys(graphs).map(uri => ({ uri: { ...new URI(uri) } }))
                : undefined,
        };
    });

    resolveIri.mockImplementation(
        async ({ path: { datasetName, graphURI, iriIdentifier } }) => {
            const hit = resourcesOf(model[datasetName]?.[graphURI]).find(
                ([resourceIri]) => resourceIri === iriIdentifier,
            );
            return { data: hit ? hit[1] : undefined };
        },
    );

    getClassInformation.mockImplementation(
        async ({ path: { datasetName, graphURI, classUUID } }) => {
            const hit = resourcesOf(model[datasetName]?.[graphURI]).find(
                ([, resourceUuid]) => resourceUuid === classUUID,
            );
            // Not found, or found but not a class (or a class that was deleted): no data.
            return { data: hit?.[2] ?? undefined };
        },
    );

    search.mockImplementation(async ({ query }) => ({
        data: {
            internalSearchResults: searchResults.filter(
                result => result.query === query.query,
            ),
            externalSearchResults: [],
        },
    }));
}

beforeEach(() => {
    vi.clearAllMocks();
});

describe("resolveClassTarget", () => {
    test("resolves an IRI within a given dataset and graph", async () => {
        mockApiFor(singleGraphModel);

        const target = await resolveClassTarget({
            dataset: "profiles",
            graph: "https://cim.example.org/EQ",
            classRef: CLASS_IRI,
        });

        expect(target).toEqual({
            datasetName: "profiles",
            graphUri: "https://cim.example.org/EQ",
            packageUUID: PACKAGE_UUID,
            classUUID: CLASS_UUID,
        });
    });

    test("searches all datasets and graphs when none are given", async () => {
        const model = {
            other: { "https://cim.example.org/SSH": {} },
            ...singleGraphModel,
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target?.datasetName).toBe("profiles");
        expect(target?.classUUID).toBe(CLASS_UUID);
    });

    test("restricts the search to the given dataset", async () => {
        const model = {
            other: {
                "https://cim.example.org/EQ": {
                    [CLASS_IRI]: {
                        uuid: CLASS_UUID,
                        package: { uuid: PACKAGE_UUID },
                    },
                },
            },
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: "profiles",
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target).toBeNull();
    });

    test("restricts the search to the given graph across datasets", async () => {
        // What an external tool sends when it knows which profile a class should open in, but not
        // what the dataset holding it is called in this session (a loaded snapshot, say).
        const model = {
            other: {
                "https://cim.example.org/SSH": {
                    [CLASS_IRI]: {
                        uuid: "0d0d0d0d-1111-4222-8333-444444444444",
                        package: { uuid: PACKAGE_UUID },
                    },
                },
            },
            profiles: singleGraphModel.profiles,
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: null,
            graph: "https://cim.example.org/EQ",
            classRef: CLASS_IRI,
        });

        expect(target?.datasetName).toBe("profiles");
        expect(target?.graphUri).toBe("https://cim.example.org/EQ");
        expect(target?.classUUID).toBe(CLASS_UUID);
    });

    test("returns null when the given graph does not hold the class", async () => {
        mockApiFor(singleGraphModel);

        const target = await resolveClassTarget({
            dataset: null,
            graph: "https://cim.example.org/SSH",
            classRef: CLASS_IRI,
        });

        expect(target).toBeNull();
    });

    test("accepts an rdfa:uuid instead of an IRI", async () => {
        mockApiFor(singleGraphModel);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: CLASS_UUID,
        });

        expect(target?.classUUID).toBe(CLASS_UUID);
        expect(target?.packageUUID).toBe(PACKAGE_UUID);
    });

    test("returns null packageUUID for classes without a package", async () => {
        const model = {
            profiles: {
                "https://cim.example.org/EQ": {
                    [CLASS_IRI]: { uuid: CLASS_UUID },
                },
            },
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target?.packageUUID).toBeNull();
    });

    test("returns null for an unknown class", async () => {
        mockApiFor(singleGraphModel);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: "https://cim.example.org/CIM#DoesNotExist",
        });

        expect(target).toBeNull();
    });

    test("keeps looking past a deleted class instead of failing", async () => {
        // Deleting a class leaves its rdfa:uuid triple behind, so the IRI still resolves and the
        // class lookup answers with an empty body.
        const model = {
            stale: {
                "https://cim.example.org/EQ": {
                    [CLASS_IRI]: { uuid: CLASS_UUID, deleted: true },
                },
            },
            profiles: singleGraphModel.profiles,
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target?.datasetName).toBe("profiles");
    });

    test("returns null when every candidate class was deleted", async () => {
        const model = {
            stale: {
                "https://cim.example.org/EQ": {
                    [CLASS_IRI]: { uuid: CLASS_UUID, deleted: true },
                },
            },
        };
        mockApiFor(model);

        const target = await resolveClassTarget({
            dataset: null,
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target).toBeNull();
    });
});

describe("resolveTermTarget", () => {
    const attributeModel = {
        profiles: {
            "https://cim.example.org/EQ": {
                [CLASS_IRI]: {
                    uuid: CLASS_UUID,
                    package: { uuid: PACKAGE_UUID, label: "Wires" },
                    attributes: [{ iri: ATTRIBUTE_IRI, uuid: ATTRIBUTE_UUID }],
                },
            },
        },
    };

    const attributeHit = {
        query: "r",
        datasetName: "profiles",
        graphUri: "https://cim.example.org/EQ",
        packageUUID: PACKAGE_UUID,
        parentClassUUID: CLASS_UUID,
        parentClassUri: {
            prefix: "https://cim.example.org/CIM#",
            suffix: "ACLineSegment",
        },
        type: "ATTRIBUTE",
        uri: {
            prefix: "https://cim.example.org/CIM#",
            suffix: "ACLineSegment.r",
        },
        uuid: ATTRIBUTE_UUID,
    };

    test("resolves a class to itself", async () => {
        mockApiFor(singleGraphModel);

        const target = await resolveTermTarget({
            dataset: null,
            graph: null,
            ref: CLASS_IRI,
        });

        expect(target).toEqual({
            datasetName: "profiles",
            graphUri: "https://cim.example.org/EQ",
            packageUUID: PACKAGE_UUID,
            classUUID: CLASS_UUID,
            propertyUUID: null,
            type: "CLASS",
        });
    });

    test("resolves an attribute to its declaring class", async () => {
        mockApiFor(attributeModel, { searchResults: [attributeHit] });

        const target = await resolveTermTarget({
            dataset: null,
            graph: null,
            ref: ATTRIBUTE_IRI,
        });

        expect(target).toEqual({
            datasetName: "profiles",
            graphUri: "https://cim.example.org/EQ",
            packageUUID: PACKAGE_UUID,
            classUUID: CLASS_UUID,
            propertyUUID: ATTRIBUTE_UUID,
            type: "ATTRIBUTE",
        });
    });

    test("falls back to the whole local name when the label is not the part after the dot", async () => {
        mockApiFor(attributeModel, {
            searchResults: [{ ...attributeHit, query: "ACLineSegment.r" }],
        });

        const target = await resolveTermTarget({
            dataset: null,
            graph: null,
            ref: ATTRIBUTE_IRI,
        });

        expect(target?.propertyUUID).toBe(ATTRIBUTE_UUID);
    });

    test("ignores search hits for a different IRI that share the label", async () => {
        mockApiFor(attributeModel, {
            searchResults: [
                {
                    ...attributeHit,
                    uri: {
                        prefix: "https://cim.example.org/CIM#",
                        suffix: "PowerTransformerEnd.r",
                    },
                    uuid: "0f0e0d0c-0b0a-4908-8706-050403020100",
                },
            ],
        });

        const target = await resolveTermTarget({
            dataset: null,
            graph: null,
            ref: ATTRIBUTE_IRI,
        });

        expect(target).toBeNull();
    });

    test("returns null for a uuid that is not a class", async () => {
        mockApiFor(attributeModel, { searchResults: [attributeHit] });

        const target = await resolveTermTarget({
            dataset: null,
            graph: null,
            ref: ATTRIBUTE_UUID,
        });

        expect(target).toBeNull();
    });
});
