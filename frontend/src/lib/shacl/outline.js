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
 * The shapes a Turtle document declares, for navigating it.
 *
 * Read off the text rather than the parsed graph on purpose. An outline is wanted while the
 * document is being edited, which is exactly when it does not parse; and the entry has to carry
 * the line it was written on, which a graph does not remember. Official constraints files run to
 * hundreds of shapes, so scrolling is not an alternative.
 *
 * The cost of reading text is that this recognises Turtle written the way people and serialisers
 * actually write it — one subject per statement, starting in the first column — rather than every
 * document the grammar allows. A subject that does not start a line is simply not listed.
 */

/**
 * The part of a name after its first character: dots are allowed inside it but not at its end,
 * where a dot is the statement's full stop. `eq:GeneratingUnit.ratedGrossMaxP-datatype` is one
 * name — which is how the official files name every rule — not `eq:GeneratingUnit` and noise.
 */
const NAME_TAIL = String.raw`(?:[^\s;,.]+(?:\.+[^\s;,.]+)*)?`;

/** A term: an absolute IRI, a blank node label, or a prefixed name (possibly with no prefix). */
const TERM = String.raw`(<[^>\s]*>|_:${NAME_TAIL}|(?:[A-Za-z_][\w.-]*)?:${NAME_TAIL})`;

const SUBJECT = new RegExp(`^${TERM}`);

const TARGET_CLASS = new RegExp(String.raw`\bsh:targetClass\s+${TERM}`);

/** `a`, `rdf:type` or its full IRI, then one of the two shape classes among the objects. */
const SHAPE_KIND = new RegExp(
    String.raw`(?:^|[\s;])(?:a|rdf:type|<http://www\.w3\.org/1999/02/22-rdf-syntax-ns#type>)\s+` +
        String.raw`(?:[^;]*?[\s,])?(?:sh:|<http://www\.w3\.org/ns/shacl#)(NodeShape|PropertyShape)\b`,
);

/** What makes a subject a shape when it does not say so with a type. */
const PROPERTY_SHAPE_HINT = /\bsh:path\b/;
const NODE_SHAPE_HINT =
    /\bsh:(?:targetClass|targetNode|targetSubjectsOf|targetObjectsOf|property)\b/;

/**
 * Every subject that starts a line, with the line it starts on.
 *
 * The outline's reading without deciding which subjects are shapes, for questions about any
 * subject — "where in this document is it defined?" above all.
 */
export function extractSubjects(turtle) {
    const lines = (turtle ?? "").split("\n");
    const subjects = [];
    let inLongString = false;

    lines.forEach((line, index) => {
        const wasInString = inLongString;
        inLongString = nextStringState(line, inLongString);
        if (wasInString || line.startsWith("#") || line.startsWith("@")) {
            return;
        }
        const subject = SUBJECT.exec(line);
        if (subject) {
            subjects.push({ name: subject[1], line: index + 1 });
        }
    });
    return subjects;
}

/**
 * @param turtle the document's text
 * @returns `{ name, line, targetClass, kind }` per shape, in the order they appear. `line` is
 *     1-based, so it can be handed straight to the editor. Subjects that are not shapes — the
 *     ontology header, a property group — are left out.
 */
export function extractOutline(turtle) {
    const lines = (turtle ?? "").split("\n");
    const subjects = extractSubjects(turtle);

    return subjects
        .map((subject, index) => {
            const until = subjects[index + 1]?.line ?? lines.length + 1;
            const block = lines.slice(subject.line - 1, until - 1).join("\n");
            return {
                ...subject,
                targetClass: TARGET_CLASS.exec(block)?.[1] ?? null,
                kind: SHAPE_KIND.exec(block)?.[1] ?? inferredKind(block),
            };
        })
        .filter(shape => shape.kind !== null);
}

/** SHACL recognises an untyped shape by what it says, so the outline does too. */
function inferredKind(block) {
    if (PROPERTY_SHAPE_HINT.test(block)) {
        return "PropertyShape";
    }
    if (NODE_SHAPE_HINT.test(block)) {
        return "NodeShape";
    }
    return null;
}

/**
 * Whether the text after this line is inside a triple-quoted string.
 *
 * Needed because the SPARQL in a `sh:select` block is free to start its lines in the first
 * column, and `?s cim:x ?o` there is not a new subject.
 */
function nextStringState(line, inLongString) {
    const delimiters = line.match(/"""|'''/g) ?? [];
    return delimiters.length % 2 === 0 ? inLongString : !inLongString;
}
