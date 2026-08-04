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

import { URI } from "$lib/models/dto/index.ts";

/**
 * The kind of model element a deep link resolved to, mirroring the search API's ResultType.
 */
export const TermType = {
    CLASS: "CLASS",
    ATTRIBUTE: "ATTRIBUTE",
    ASSOCIATION: "ASSOCIATION",
    ENUMTYPE: "ENUMTYPE",
};

/**
 * @typedef {object} DeepLinkTarget
 * @property {string} datasetName
 * @property {string} graphUri
 * @property {string | null} packageUUID
 * @property {string} classUUID the class to open — for a property, the class that declares it
 * @property {string | null} propertyUUID the attribute/association/enum entry to focus, if any
 * @property {string} type one of {@link TermType}
 */

/**
 * Resolves a deep-link reference to a navigable target.
 *
 * A reference is either an IRI or an rdfa:uuid. Classes are resolved directly; an IRI that is not
 * a class is looked up as an attribute, association or enum entry, in which case the target is the
 * declaring class plus the property to focus. External tools (e.g. the CIMNotebook IDE extensions)
 * link into the editor this way without knowing which dataset a schema was imported into.
 *
 * @param {object} backend a BackendConnection
 * @param {{dataset: string | null, graph: string | null, ref: string}} params
 * @returns {Promise<DeepLinkTarget | null>}
 */
export async function resolveTermTarget(backend, { dataset, graph, ref }) {
    const classTarget = await resolveClassTarget(backend, {
        dataset,
        graph,
        classRef: ref,
    });
    if (classTarget) {
        return { ...classTarget, propertyUUID: null, type: TermType.CLASS };
    }
    // A bare uuid carries no name to search for, so properties are addressable by IRI only.
    if (validate(ref)) {
        return null;
    }
    return await resolvePropertyTarget(backend, { dataset, graph, iri: ref });
}

/**
 * Resolves a `class` deep-link reference (an IRI or an rdfa:uuid) to a navigable class target.
 *
 * When `dataset` and `graph` are given, only that graph is consulted. Either on its own narrows the
 * search: `graph` alone is what an external tool can say when it knows which profile a class should
 * be opened in but not what the dataset is called in this session. Without both, every remaining
 * graph is probed until the class is found.
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
            : await listGraphPairs(backend, dataset, graph);
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

/**
 * Resolves an attribute, association or enum entry IRI through the search API, which reports the
 * declaring class (`parentClassUUID`) alongside every match. Search matches labels, so the IRI's
 * local name is queried and the results are then narrowed to the exact IRI.
 *
 * @param {object} backend a BackendConnection
 * @param {{dataset: string | null, graph: string | null, iri: string}} params
 * @returns {Promise<DeepLinkTarget | null>}
 */
async function resolvePropertyTarget(backend, { dataset, graph, iri }) {
    for (const query of searchQueriesFor(iri)) {
        const results = await trySearch(backend, query, {
            datasetName: dataset,
            graphUri: graph,
            packageUUID: null,
        });
        const hit = results.find(
            result => result.parentClassUUID && sameIri(result.uri, iri),
        );
        if (hit) {
            return {
                datasetName: hit.datasetName,
                graphUri: graphUriOf(hit.graphUri),
                packageUUID: hit.packageUUID ?? null,
                classUUID: hit.parentClassUUID,
                propertyUUID: hit.uuid ?? null,
                type: hit.type,
            };
        }
    }
    return null;
}

/**
 * The search queries to try for a property IRI, most specific first: CIM names a property
 * `<Class>.<property>` and labels it with the part after the dot, but a schema may just as well
 * label it with the whole local name.
 *
 * @param {string} iri
 * @returns {string[]}
 */
function searchQueriesFor(iri) {
    const localName = iri.split(/[#/]/).pop() ?? "";
    const afterDot = localName.slice(localName.lastIndexOf(".") + 1);
    return [...new Set([afterDot, localName])].filter(
        query => query.length > 0,
    );
}

/**
 * @param {string | {prefix: string | null, suffix: string}} uri a search result's URI
 * @param {string} iri
 * @returns {boolean} whether both denote the same IRI
 */
function sameIri(uri, iri) {
    if (!uri) {
        return false;
    }
    try {
        return graphUriOf(uri) === iri;
    } catch {
        return false;
    }
}

/** All (dataset, graph) pairs of the session, optionally restricted by dataset and/or graph. */
async function listGraphPairs(backend, datasetFilter, graphFilter) {
    const datasets = await readJson(await backend.getDatasetNames());
    if (!datasets) {
        return [];
    }
    const pairs = [];
    for (const datasetName of datasets) {
        if (datasetFilter && datasetName !== datasetFilter) {
            continue;
        }
        const graphs = await readJson(await backend.getGraphNames(datasetName));
        if (!graphs) {
            continue;
        }
        for (const graph of graphs) {
            const graphUri = graphUriOf(graph);
            if (graphFilter && graphUri !== graphFilter) {
                continue;
            }
            pairs.push([datasetName, graphUri]);
        }
    }
    return pairs;
}

/**
 * The graph list endpoint serves URIs as `{prefix, suffix}` objects, not strings — interpolating
 * one straight into a request path yields "[object Object]" and every lookup misses.
 *
 * @param {string | {prefix: string | null, suffix: string}} graph
 * @returns {string} the full graph IRI
 */
function graphUriOf(graph) {
    return typeof graph === "string" ? graph : new URI(graph).toString();
}

async function tryResolveIri(backend, datasetName, graphUri, iri) {
    const res = await backend.resolveIri(datasetName, graphUri, iri);
    if (!res?.ok) {
        return null;
    }
    const uuid = (await res.text()).trim();
    return uuid.length > 0 ? uuid : null;
}

async function tryGetClassInfo(backend, datasetName, graphUri, classUUID) {
    return await readJson(
        await backend.getClassInfo(datasetName, graphUri, classUUID),
    );
}

async function trySearch(backend, query, filter) {
    const results = await readJson(
        await backend.getSearchResults(query, filter),
    );
    // External results come from other sources than the session's own graphs and carry no
    // dataset/graph to navigate to.
    return results?.internalSearchResults ?? [];
}

/**
 * Reads a JSON response body, tolerating the way this API reports "no such thing": a uuid that
 * belongs to something other than a class — or to a class that has since been deleted — answers
 * `200` with an *empty body*, which `Response.json()` rejects on.
 *
 * @param {Response | undefined} res
 * @returns {Promise<any | null>} the parsed body, or null when there is nothing to parse
 */
async function readJson(res) {
    if (!res?.ok) {
        return null;
    }
    const body = await res.text();
    if (body.trim().length === 0) {
        return null;
    }
    try {
        return JSON.parse(body);
    } catch {
        return null;
    }
}
