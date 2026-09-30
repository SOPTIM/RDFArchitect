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
 * The prefix as the REST API writes it, with a trailing colon.
 * @param prefix The prefix, with or without a trailing colon
 * @returns {string} The prefix with a trailing colon
 */
export function withColon(prefix) {
    const trimmed = (prefix ?? "").trim();
    return trimmed.endsWith(":") ? trimmed : `${trimmed}:`;
}

/**
 * The prefix as RDF stores it, without a trailing colon.
 * @param prefix The prefix, with or without a trailing colon
 * @returns {string} The prefix without a trailing colon
 */
export function withoutColon(prefix) {
    return (prefix ?? "").trim().replace(/:$/, "");
}

/**
 * Formats the given namespace as "(substitutedPrefix) prefix", e.g. "(ex) http://example.com/"
 * @param namespace The namespace to format, must have the properties "prefix" and "substitutedPrefix"
 * @returns {string} The formatted namespace string
 */
export function getNsPrefixNsUriString(namespace) {
    return `(${withoutColon(namespace.substitutedPrefix)}) ${namespace.prefix}`;
}
