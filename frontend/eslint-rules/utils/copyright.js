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

import fs from "node:fs";
import { fileURLToPath } from "node:url";

const NOTICE_PATH = fileURLToPath(
    new URL("../../.idea/copyright/Apache_2_0_License.xml", import.meta.url),
);

let cachedNotice = null;

function decodeNotice(raw) {
    return raw
        .replaceAll("&#10;", "\n")
        .replaceAll("&quot;", '"')
        .replaceAll("&apos;", "'")
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&amp;", "&");
}

function trimBlankEdges(lines) {
    let start = 0;
    let end = lines.length;
    while (start < end && lines[start].trim() === "") start += 1;
    while (end > start && lines[end - 1].trim() === "") end -= 1;
    return lines.slice(start, end);
}

export function normalizeNoticeLines(notice) {
    const lines = notice.replaceAll("\r", "").split("\n");
    return trimBlankEdges(lines.map(line => line.trim()));
}

export function getCopyrightNotice() {
    if (cachedNotice) return cachedNotice;

    const xml = fs.readFileSync(NOTICE_PATH, "utf8");
    const match = xml.match(/<option name="notice" value="([^"]*)"/);
    if (!match) {
        throw new Error(
            "Could not find copyright notice in .idea/copyright/Apache_2_0_License.xml",
        );
    }

    const decoded = decodeNotice(match[1]);
    const lines = normalizeNoticeLines(decoded);
    cachedNotice = {
        raw: decoded,
        lines,
        normalized: lines.join("\n"),
    };
    return cachedNotice;
}

function formatLine(line, style) {
    if (line.trim() === "") return style === "html" ? "  -" : " *";
    return style === "html" ? `  -    ${line}` : ` *    ${line}`;
}

export function buildCopyrightComment(style) {
    const { lines } = getCopyrightNotice();
    const commentLines = lines.map(line => formatLine(line, style));
    return style === "html"
        ? `<!--\n${commentLines.join("\n")}\n  -->`
        : `/*\n${commentLines.join("\n")}\n */`;
}

function normalizeLine(line, style) {
    const withoutMarker =
        style === "html"
            ? line.replace(/^\s*-?\s?/, "")
            : line.replace(/^\s*\*?\s?/, "");
    return withoutMarker.trim();
}

export function normalizeCommentBody(body, style) {
    const lines = body.replaceAll("\r", "").split("\n");
    const normalized = trimBlankEdges(
        lines.map(line => normalizeLine(line, style)),
    );
    return normalized.join("\n");
}
