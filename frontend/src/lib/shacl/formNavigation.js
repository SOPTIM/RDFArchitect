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
 * Finding your way around a form with a hundred and forty-five cards in it.
 *
 * An official profile is not a document you scroll. The tab could show one from the day it shipped
 * and there was no way to get to a particular shape in it, which made every other thing the form
 * can do unreachable in practice. Kept out of the components so it can be tested without a DOM.
 */

import { abbreviate } from "$lib/shacl/turtleTerms.js";

/**
 * The shapes a filter leaves, in the order they were given.
 *
 * Matched against everything a person might remember about a shape: what it is called, what it
 * applies to, and what its rules are about — a shape is usually looked for by the property that
 * went wrong, which lives on a rule rather than on the shape.
 */
export function matchingShapes(
    shapes,
    { filter = "", lockedOnly = false } = {},
    prefixes = {},
) {
    const needle = filter.trim().toLowerCase();
    return (shapes ?? []).filter(shape => {
        if (lockedOnly && shape.editable !== false && !hasLockedRule(shape)) {
            return false;
        }
        return needle === "" || shapeMatches(shape, needle, prefixes);
    });
}

/** The named rules a filter leaves. They have no target class, so less to match on. */
export function matchingRules(
    rules,
    { filter = "", lockedOnly = false } = {},
    prefixes = {},
) {
    const needle = filter.trim().toLowerCase();
    return (rules ?? []).filter(rule => {
        if (lockedOnly && rule.editable !== false) {
            return false;
        }
        return needle === "" || ruleMatches(rule, needle, prefixes);
    });
}

function hasLockedRule(shape) {
    return (shape.properties ?? []).some(rule => rule.editable === false);
}

function shapeMatches(shape, needle, prefixes) {
    return (
        matches(shape.iri, needle, prefixes) ||
        matches(shape.name, needle, prefixes) ||
        matches(shape.message, needle, prefixes) ||
        (shape.targetClasses ?? []).some(iri =>
            matches(iri, needle, prefixes),
        ) ||
        (shape.properties ?? []).some(rule =>
            ruleMatches(rule, needle, prefixes),
        )
    );
}

function ruleMatches(rule, needle, prefixes) {
    return (
        matches(rule.path, needle, prefixes) ||
        matches(rule.iri, needle, prefixes) ||
        matches(rule.name, needle, prefixes) ||
        matches(rule.message, needle, prefixes)
    );
}

/**
 * Whether one value contains the text typed.
 *
 * An IRI is matched both whole and as the document writes it, because `cim:ACLineSegment` is what
 * is on the card and `http://iec.ch/TC57/CIM100#ACLineSegment` is what someone pasting from the
 * Turtle view has in the clipboard.
 */
function matches(value, needle, prefixes) {
    if (!value) {
        return false;
    }
    if (value.toLowerCase().includes(needle)) {
        return true;
    }
    return abbreviate(value, prefixes).toLowerCase().includes(needle);
}

/**
 * The card the document writes a given line in, or `null` when nothing is written above it.
 *
 * The last thing that starts at or before the line, which is where a reader would say they are.
 * Named rules take part as well as node shapes: in a `-Con-Simple-` profile most of the lines in
 * the file are inside one, and answering "the node shape further up" would be a wrong answer
 * rather than an approximate one.
 */
export function entryAtLine(shapes, propertyShapes, line) {
    if (!Number.isFinite(line)) {
        return null;
    }
    const entries = [
        ...(shapes ?? []).map(shape => ({
            kind: "shape",
            iri: shape.iri,
            line: shape.line,
        })),
        ...(propertyShapes ?? []).map(rule => ({
            kind: "rule",
            iri: rule.iri,
            line: rule.line,
        })),
    ]
        .filter(entry => Number.isFinite(entry.line) && entry.line <= line)
        .sort((a, b) => a.line - b.line);
    return entries.length ? entries[entries.length - 1] : null;
}
