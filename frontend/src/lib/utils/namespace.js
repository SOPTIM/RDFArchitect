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
 * Formats the given namespace as "(substitutedPrefix) prefix", e.g. "(ex) http://example.com/"
 * @param namespace The namespace to format, must have the properties "prefix" and "substitutedPrefix"
 * @returns {string} The formatted namespace string
 */
export function getNsPrefixNsUriString(namespace) {
    let namespacePrefix = namespace.substitutedPrefix;
    if (namespacePrefix && namespacePrefix.endsWith(":")) {
        namespacePrefix = namespacePrefix.slice(0, -1);
    }
    const namespaceUri = namespace.prefix;
    return `(${namespacePrefix}) ${namespaceUri}`;
}
