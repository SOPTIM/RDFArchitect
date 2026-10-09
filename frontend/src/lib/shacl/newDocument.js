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
 * What a new constraints document starts with.
 *
 * An empty document binds no prefixes, so the first term anyone writes into it — `sh:NodeShape`,
 * `cim:ACLineSegment` — is an undefined prefix, and completion can only offer full IRIs. The
 * workspace already knows the namespaces its schemas use, so a new document starts with those.
 */

const STANDARD = [
    ["sh", "http://www.w3.org/ns/shacl#"],
    ["rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"],
    ["rdfs", "http://www.w3.org/2000/01/rdf-schema#"],
    ["xsd", "http://www.w3.org/2001/XMLSchema#"],
];

/** Turtle's PN_PREFIX, restricted to ASCII: what may stand before the colon. */
const PREFIX_NAME = /^[A-Za-z]([\w.-]*[\w-])?$/;

/**
 * Where the document's own shapes live, derived from the schema they constrain.
 *
 * The official releases name theirs `<profile>/Constraints#`; a graph URI with a fragment keeps
 * the fragment as a path segment, so two schemas under one base do not share a namespace.
 */
export function shapesNamespace(graphUri) {
    const base = (graphUri ?? "").replace("#", "/").replace(/[#/]+$/, "");
    return `${base || "urn:rdfa:shapes"}/Constraints#`;
}

/**
 * @param graphUri the schema the document constrains
 * @param namespaces the workspace's namespaces, as `{ prefix: iri, substitutedPrefix: name }`
 * @param keyword the schema's dcat:keyword, which names the shapes prefix when it can
 */
export function newDocumentText({ graphUri, namespaces = [], keyword = null }) {
    const bindings = new Map(STANDARD);
    const iris = new Set(bindings.values());
    for (const namespace of namespaces ?? []) {
        const name = (namespace?.substitutedPrefix ?? "").replace(/:$/, "");
        const iri = namespace?.prefix;
        if (!iri || !PREFIX_NAME.test(name) || bindings.has(name)) {
            continue;
        }
        if (iris.has(iri)) {
            continue;
        }
        bindings.set(name, iri);
        iris.add(iri);
    }

    const shapes = shapesNamespace(
        profileNamespace(namespaces, keyword) ?? graphUri,
    );
    if (!iris.has(shapes)) {
        bindings.set(shapesPrefix(keyword, bindings), shapes);
    }

    const width = Math.max(...[...bindings.keys()].map(name => name.length));
    const lines = [...bindings].map(
        ([name, iri]) => `@prefix ${`${name}:`.padEnd(width + 1)} <${iri}> .`,
    );
    return `${lines.join("\n")}\n\n`;
}

/**
 * The namespace the schema's own terms live in, found as the one the workspace binds to its keyword
 * — `eq:` for the CGMES Equipment profile.
 *
 * Preferred over the graph URI, which for an imported file is a name the importer made up
 * (`http://graph#61970_…`): built from that, a new document's shapes lived under `http://graph/…`,
 * where the official file puts them under the profile, `…/CoreEquipment-EU/Constraints#`.
 */
function profileNamespace(namespaces, keyword) {
    const wanted = (keyword ?? "").toLowerCase();
    // `cim:` is the namespace every profile's classes share, not one profile's own.
    if (!wanted || wanted === "cim") {
        return null;
    }
    const match = (namespaces ?? []).find(
        namespace =>
            (namespace?.substitutedPrefix ?? "").replace(/:$/, "") === wanted,
    );
    return match?.prefix ?? null;
}

function shapesPrefix(keyword, bindings) {
    const wanted = (keyword ?? "").toLowerCase();
    if (PREFIX_NAME.test(wanted) && !bindings.has(wanted)) {
        return wanted;
    }
    let name = "shapes";
    for (let suffix = 2; bindings.has(name); suffix += 1) {
        name = `shapes${suffix}`;
    }
    return name;
}
