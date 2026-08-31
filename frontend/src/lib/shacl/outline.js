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

/** A term in subject position: a prefixed name, an absolute IRI, or a blank node label. */
const SUBJECT = /^(<[^>\s]*>|_:[^\s;,.]+|[A-Za-z_][\w.-]*:[^\s;,.]*)/;

const TARGET_CLASS =
    /\bsh:targetClass\s+(<[^>\s]*>|[A-Za-z_][\w.-]*:[^\s;,.]+)/;

const SHAPE_KIND = /\ba\s+sh:(NodeShape|PropertyShape)\b/;

/**
 * @param turtle the document's text
 * @returns `{ name, line, targetClass, kind }` per shape, in the order they appear. `line` is
 *     1-based, so it can be handed straight to the editor.
 */
export function extractOutline(turtle) {
    const lines = (turtle ?? "").split("\n");
    const shapes = [];
    let inLongString = false;

    lines.forEach((line, index) => {
        const wasInString = inLongString;
        inLongString = nextStringState(line, inLongString);
        if (wasInString || line.startsWith("#") || line.startsWith("@")) {
            return;
        }
        const subject = SUBJECT.exec(line);
        if (!subject) {
            return;
        }
        shapes.push({
            name: subject[1],
            line: index + 1,
            targetClass: null,
            kind: null,
        });
    });

    return shapes.map((shape, index) => {
        const until = shapes[index + 1]?.line ?? lines.length + 1;
        const block = lines.slice(shape.line - 1, until - 1).join("\n");
        return {
            ...shape,
            targetClass: TARGET_CLASS.exec(block)?.[1] ?? null,
            kind: SHAPE_KIND.exec(block)?.[1] ?? null,
        };
    });
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
