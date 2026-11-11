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
 * What a schema of an imported file is called.
 *
 * The namespace is the backend's `RDFA.GRAPH_URI` and there is no contract carrying it, so the two
 * are pinned from both sides instead: `graphUri.test.js` here and `RDFATest` there each assert this
 * literal, and whichever side is changed alone fails.
 */
export const GRAPH_NAMESPACE_URI = "http://graph#";

const DEFAULT_GRAPH_NAME = "graph";

/** The file name as a graph name: without path, without extension, non-word characters replaced. */
export function graphNameFrom(fileName) {
    if (!fileName) {
        return DEFAULT_GRAPH_NAME;
    }
    const normalized = fileName.split(/[\\/]/).pop() ?? fileName;
    const lastDotIndex = normalized.lastIndexOf(".");
    const baseName =
        lastDotIndex === -1
            ? normalized
            : normalized.substring(0, lastDotIndex);
    const sanitized = baseName.replace(/\W/g, "_");
    return sanitized.trim() ? sanitized : DEFAULT_GRAPH_NAME;
}

/** The graph a file is imported as, unless the caller renames it. */
export function graphUriFrom(fileName) {
    return `${GRAPH_NAMESPACE_URI}${graphNameFrom(fileName)}`;
}

/**
 * What the caller typed, as a full graph URI. A name is put in the graph namespace; something
 * that already names a scheme is left as it is, so a schema can be imported under any URI.
 */
export function asGraphUri(graphUri, fallbackName) {
    const trimmed = graphUri?.trim();
    if (!trimmed) {
        return graphUriFrom(fallbackName);
    }
    if (trimmed.includes("://")) {
        return trimmed;
    }
    return `${GRAPH_NAMESPACE_URI}${trimmed}`;
}
