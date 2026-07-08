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

import { validate } from "uuid";

/**
 * Resolves a `class` deep-link reference (an IRI or an rdfa:uuid) to a navigable class target.
 *
 * When `dataset`/`graph` are given, only that graph is consulted; otherwise every graph of every
 * dataset in the session (optionally narrowed to `dataset`) is probed until the class is found.
 * External tools (e.g. the CIMNotebook IDE extensions) link into the editor this way without
 * knowing which dataset a schema was imported into.
 *
 * @param {object} backend a BackendConnection
 * @param {{dataset: string | null, graph: string | null, classRef: string}} params
 * @returns {Promise<{datasetName: string, graphUri: string, packageUUID: string | null, classUUID: string} | null>}
 */
export async function resolveClassTarget(
    backend,
    { dataset, graph, classRef },
) {
    const candidates =
        dataset && graph
            ? [[dataset, graph]]
            : await listGraphPairs(backend, dataset);
    for (const [datasetName, graphUri] of candidates) {
        const classUUID = validate(classRef)
            ? classRef
            : await tryResolveIri(backend, datasetName, graphUri, classRef);
        if (!classUUID) {
            continue;
        }
        const info = await tryGetClassInfo(
            backend,
            datasetName,
            graphUri,
            classUUID,
        );
        if (!info) {
            continue;
        }
        return {
            datasetName,
            graphUri,
            packageUUID: info.package?.uuid ?? null,
            classUUID,
        };
    }
    return null;
}

/** All (dataset, graph) pairs of the session, optionally restricted to one dataset. */
async function listGraphPairs(backend, datasetFilter) {
    const res = await backend.getDatasetNames();
    if (!res.ok) {
        return [];
    }
    const datasets = await res.json();
    const pairs = [];
    for (const datasetName of datasets) {
        if (datasetFilter && datasetName !== datasetFilter) {
            continue;
        }
        const graphsRes = await backend.getGraphNames(datasetName);
        if (!graphsRes.ok) {
            continue;
        }
        for (const graphUri of await graphsRes.json()) {
            pairs.push([datasetName, graphUri]);
        }
    }
    return pairs;
}

async function tryResolveIri(backend, datasetName, graphUri, iri) {
    const res = await backend.resolveIri(datasetName, graphUri, iri);
    return res.ok ? await res.text() : null;
}

async function tryGetClassInfo(backend, datasetName, graphUri, classUUID) {
    const res = await backend.getClassInfo(datasetName, graphUri, classUUID);
    return res.ok ? await res.json() : null;
}
