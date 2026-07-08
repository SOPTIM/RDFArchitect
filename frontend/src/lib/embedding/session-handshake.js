/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/**
 * Lets an application that embeds RDFArchitect learn which backend session this app is using.
 *
 * Datasets live in a session, so a tool outside the browser — the CIMNotebook IDE extensions, for
 * instance — can only read the datasets you are editing if it addresses *this* session. The host
 * asks over `postMessage`, and the answer goes back to the exact origin that asked:
 *
 * ```
 * host → app:  { type: "rdfa:session-request" }
 * app  → host: { type: "rdfa:session", id: "<session id>" }
 * ```
 *
 * The id is the session cookie's value, so anyone holding it can act as that session. Two things
 * therefore gate the answer: the app must be embedded at all, and the deployment must opt in with
 * `PUBLIC_EMBED_SESSION_HANDSHAKE=true`. Without the opt-in, a page that embeds RDFArchitect in an
 * iframe would be able to read the visitor's datasets — so leave it off unless the embedder is
 * trusted (see the admin guide).
 */

/** Message a host sends to ask for the session. */
export const SESSION_REQUEST = "rdfa:session-request";

/** Message this app sends back with the session id. */
export const SESSION_RESPONSE = "rdfa:session";

/**
 * Answers session requests from an embedding host for as long as the returned function is not
 * called.
 *
 * @param {object} options
 * @param {boolean} options.enabled the deployment's opt-in
 * @param {() => Promise<string | null>} options.fetchSessionId reads the current session id
 * @param {Window} [options.target] the window to listen on (this app's own window)
 * @param {boolean} [options.embedded] whether this app runs inside a host; defaults to detecting an
 *     iframe. A host that loads the app top-level (IntelliJ's JCEF tool window) scripts its browser
 *     directly and never needs this handshake.
 * @returns {() => void} removes the listener
 */
export function installSessionHandshake({
    enabled,
    fetchSessionId,
    target = globalThis.window,
    embedded = isEmbedded(target),
}) {
    if (!enabled || !embedded || !target) {
        return () => {};
    }
    const onMessage = async event => {
        if (event?.data?.type !== SESSION_REQUEST) {
            return;
        }
        // Reply only to whoever asked, never to "*": the id is a credential.
        const origin = event.origin;
        const source = event.source;
        if (!source || !origin || origin === "null") {
            return;
        }
        const id = await fetchSessionId();
        if (id) {
            source.postMessage({ type: SESSION_RESPONSE, id }, origin);
        }
    };
    target.addEventListener("message", onMessage);
    return () => target.removeEventListener("message", onMessage);
}

/** Whether this app is running inside another document (an iframe). */
export function isEmbedded(target = globalThis.window) {
    try {
        return Boolean(target) && target.parent !== target;
    } catch {
        // Cross-origin parents can throw on access; that in itself means we are embedded.
        return true;
    }
}
