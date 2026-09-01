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
 * Linking into the constraints workbench at a particular rule.
 *
 * The workbench is a route rather than a dialog, so the target travels in the URL. That also makes
 * the link shareable and survivable across a reload, which a piece of shared state would not be.
 */

export const WORKBENCH_PATH = "/shacl";

/** `/shacl`, optionally opening one document and putting the cursor on one line. */
export function workbenchHref(documentId = null, line = null) {
    const query = new URLSearchParams();
    if (documentId) {
        query.set("document", documentId);
    }
    if (documentId && line) {
        query.set("line", String(line));
    }
    const search = query.toString();
    return search === "" ? WORKBENCH_PATH : `${WORKBENCH_PATH}?${search}`;
}

/**
 * The document and line a workbench url asks for, as `{ documentId, line }`.
 *
 * A line without a document is dropped: it would be a line number in whichever document happened
 * to be open, which is worse than not scrolling at all.
 */
export function workbenchTarget(url) {
    const documentId = url?.searchParams?.get("document") ?? null;
    if (!documentId) {
        return { documentId: null, line: null };
    }
    const line = Number.parseInt(url.searchParams.get("line") ?? "", 10);
    return {
        documentId,
        line: Number.isFinite(line) && line > 0 ? line : null,
    };
}
