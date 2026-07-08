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

import { describe, expect, test } from "vitest";

import { resolveClassTarget } from "$lib/utils/deep-link.js";

const CLASS_IRI = "https://cim.example.org/CIM#ACLineSegment";
const CLASS_UUID = "8f7c2d7e-3f7a-4e1c-9a5e-2b6c1d0e9f4a";
const PACKAGE_UUID = "4a1b9c8d-7e6f-4a2b-8c3d-5e4f6a7b8c9d";

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

const ok = body => ({
    ok: true,
    json: async () => body,
    text: async () => String(body),
});
const notFound = () => ({
    ok: false,
    json: async () => ({}),
    text: async () => "not found",
});

/**
 * A BackendConnection stand-in over a {dataset: {graphUri: {iri: classInfo}}} fixture.
 * Classes are looked up by IRI (resolveIri) or by their fixture uuid (getClassInfo).
 */
function fakeBackend(model) {
    return {
        async getDatasetNames() {
            return ok(Object.keys(model));
        },
        async getGraphNames(dataset) {
            return model[dataset]
                ? ok(Object.keys(model[dataset]))
                : notFound();
        },
        async resolveIri(dataset, graph, iri) {
            const info = model[dataset]?.[graph]?.[iri];
            return info ? ok(info.uuid) : notFound();
        },
        async getClassInfo(dataset, graph, uuid) {
            const info = Object.values(model[dataset]?.[graph] ?? {}).find(
                c => c.uuid === uuid,
            );
            return info ? ok(info) : notFound();
        },
    };
}

describe("resolveClassTarget", () => {
    test("resolves an IRI within a given dataset and graph", async () => {
        const target = await resolveClassTarget(fakeBackend(singleGraphModel), {
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

        const target = await resolveClassTarget(fakeBackend(model), {
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

        const target = await resolveClassTarget(fakeBackend(model), {
            dataset: "profiles",
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target).toBeNull();
    });

    test("accepts an rdfa:uuid instead of an IRI", async () => {
        const target = await resolveClassTarget(fakeBackend(singleGraphModel), {
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

        const target = await resolveClassTarget(fakeBackend(model), {
            dataset: null,
            graph: null,
            classRef: CLASS_IRI,
        });

        expect(target?.packageUUID).toBeNull();
    });

    test("returns null for an unknown class", async () => {
        const target = await resolveClassTarget(fakeBackend(singleGraphModel), {
            dataset: null,
            graph: null,
            classRef: "https://cim.example.org/CIM#DoesNotExist",
        });

        expect(target).toBeNull();
    });
});
