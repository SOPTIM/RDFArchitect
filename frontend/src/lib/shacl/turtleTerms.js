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
 * Reading and writing CIM terms in a Turtle document.
 *
 * A schema term travels as an absolute IRI, but a document writes it as `cim:ACLineSegment` using
 * whatever prefix it happens to bind. Everything here is about crossing that gap in both
 * directions, and it is deliberately free of Monaco so it can be tested as plain text handling.
 */

/** Characters a prefixed name is made of. Turtle allows `.` inside one, as `cim:Class.attr` does. */
const NAME_CHARS = /[A-Za-z0-9_.:%-]/;

const PREFIX_DIRECTIVE =
    /^\s*(?:@prefix|PREFIX)\s+([A-Za-z][\w.-]*)?:\s*<([^>]*)>/gim;

/**
 * The namespaces a document binds, as `{ prefix: namespace }`.
 *
 * Both Turtle's `@prefix` and SPARQL's `PREFIX` spelling are accepted, because the SPARQL inside
 * `sh:select` may bind its own. The default namespace binds under the empty string.
 */
export function parsePrefixes(text) {
    const prefixes = {};
    if (!text) {
        return prefixes;
    }
    PREFIX_DIRECTIVE.lastIndex = 0;
    let match = PREFIX_DIRECTIVE.exec(text);
    while (match !== null) {
        prefixes[match[1] ?? ""] = match[2];
        match = PREFIX_DIRECTIVE.exec(text);
    }
    return prefixes;
}

/**
 * The absolute IRI a written token stands for, or `null` when it is not a term.
 *
 * A trailing `.` is retried without it: `ex:o.` at the end of a statement is a name followed by the
 * statement's full stop, and only the document's prefixes can say which of the two dots is meant.
 */
export function resolveTerm(token, prefixes) {
    if (!token) {
        return null;
    }
    if (token.startsWith("<") && token.endsWith(">")) {
        return token.slice(1, -1) || null;
    }
    const colon = token.indexOf(":");
    if (colon < 0) {
        return null;
    }
    const namespace = prefixes[token.slice(0, colon)];
    if (namespace === undefined) {
        return null;
    }
    const localName = token.slice(colon + 1);
    if (localName.endsWith(".")) {
        return namespace + localName.replace(/\.+$/, "");
    }
    return namespace + localName;
}

/**
 * The token around a 1-based column, as `{ text, startColumn, endColumn }`.
 *
 * `endColumn` is exclusive, matching how Monaco describes a range, so the pair can be handed
 * straight to a hover or a completion as the span to replace.
 */
export function tokenAt(lineText, column) {
    if (!lineText) {
        return null;
    }
    const angle = angleBracketedAt(lineText, column);
    if (angle) {
        return angle;
    }
    const index = column - 1;
    let start = index;
    while (start > 0 && NAME_CHARS.test(lineText[start - 1])) {
        start -= 1;
    }
    let end = index;
    while (end < lineText.length && NAME_CHARS.test(lineText[end])) {
        end += 1;
    }
    if (start === end) {
        return null;
    }
    return {
        text: lineText.slice(start, end),
        startColumn: start + 1,
        endColumn: end + 1,
    };
}

/** The `<...>` IRI the column sits inside, if it sits inside one. */
function angleBracketedAt(lineText, column) {
    const index = column - 1;
    const open = lineText.lastIndexOf("<", index);
    if (open < 0) {
        return null;
    }
    const close = lineText.indexOf(">", open);
    if (close < 0 || close < index) {
        return null;
    }
    return {
        text: lineText.slice(open, close + 1),
        startColumn: open + 1,
        endColumn: close + 2,
    };
}

/** The term at a position, resolved through the document's prefixes. */
export function termAt(lineText, column, prefixes) {
    const token = tokenAt(lineText, column);
    if (!token) {
        return null;
    }
    const iri = resolveTerm(token.text, prefixes);
    return iri ? { ...token, iri } : null;
}

/**
 * How a term should be written in a document that binds these prefixes.
 *
 * Falls back to the full IRI in angle brackets rather than inventing a prefix: a completion that
 * silently added `@prefix` lines would edit parts of the file the user is not looking at.
 */
export function writeTerm(term, prefixes) {
    for (const [prefix, namespace] of Object.entries(prefixes)) {
        if (namespace === term.namespace) {
            return `${prefix}:${term.localName}`;
        }
    }
    return `<${term.iri}>`;
}

/**
 * Completion entries for a document, as `{ label, insertText, detail, kind, sortText }`.
 *
 * Terms the document has no prefix for are still offered — the schema spans profiles a given file
 * may not have bound yet — but they sort last, because a full IRI is rarely what someone typing
 * `cim:` wants.
 */
export function completionEntries(terms, prefixes) {
    return (terms ?? []).map(term => {
        const written = writeTerm(term, prefixes);
        const abbreviated = !written.startsWith("<");
        return {
            label: written,
            insertText: written,
            detail: term.label ?? term.localName,
            kind: term.kind,
            sortText: `${abbreviated ? "0" : "1"}${term.localName}`,
        };
    });
}

/** The hover text for a term: what it is called, what it means, and how it may be used. */
export function hoverMarkdown(detail, prefixes) {
    if (!detail) {
        return null;
    }
    const written = writeTerm(detail, prefixes);
    const lines = [];

    const heading =
        detail.label && detail.label !== detail.localName
            ? `**\`${written}\`** — ${detail.label}`
            : `**\`${written}\`**`;
    lines.push(heading, "");

    if (detail.comment) {
        lines.push(detail.comment, "");
    }

    const rows = [];
    if (detail.domains?.length) {
        rows.push([
            "Domain",
            detail.domains.map(iri => code(iri, prefixes)).join(" "),
        ]);
    }
    if (detail.ranges?.length) {
        rows.push([
            "Range",
            detail.ranges.map(iri => code(iri, prefixes)).join(" "),
        ]);
    }
    if (detail.multiplicity) {
        rows.push(["Multiplicity", `\`${detail.multiplicity}\``]);
    }
    if (detail.profiles?.length) {
        rows.push([
            "Profile",
            detail.profiles.map(profile => `\`${profile}\``).join(" "),
        ]);
    }
    if (rows.length > 0) {
        lines.push("| | |", "|---|---|");
        rows.forEach(([name, value]) =>
            lines.push(`| **${name}** | ${value} |`),
        );
    }

    return lines.join("\n").trim();
}

function code(iri, prefixes) {
    const namespace = iri.slice(
        0,
        Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/")) + 1,
    );
    const localName = iri.slice(namespace.length);
    return `\`${writeTerm({ iri, namespace, localName }, prefixes)}\``;
}
