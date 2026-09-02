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
 * The workbench is a route rather than a dialog, so the target travels in the URL, which survives a
 * reload where a piece of shared state would not.
 *
 * The workspace and schema are deliberately not in the link: the workbench opens whichever schema
 * is selected, and a link carrying its own would have to either override that selection or disagree
 * with it. A consequence worth knowing is that the link is only meaningful within a session that
 * has the same schema selected — it is a jump, not a shareable address.
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

/**
 * Whether leaving the workbench has to be asked about first.
 *
 * Pulled out of the page because the interesting part is the exceptions, not the guard: a
 * navigation that stays on the workbench is not leaving it — following a deep link clears its
 * query that way — and a navigation the user has already agreed to must not be questioned again,
 * or agreeing would cancel the very navigation it agreed to.
 *
 * @param dirty whether the editor holds unsaved changes
 * @param toPathname where the navigation is going, or null when it leaves the app entirely
 * @param alreadyConfirmed whether this navigation is one the user has already agreed to
 */
export function leavingNeedsConfirmation({
    dirty,
    toPathname = null,
    alreadyConfirmed = false,
} = {}) {
    if (!dirty || alreadyConfirmed) {
        return false;
    }
    return toPathname !== WORKBENCH_PATH;
}
