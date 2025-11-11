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

export function shortenIri(prefixes, iri) {
    if (!iri) {
        return iri ?? "";
    }
    if (!Array.isArray(prefixes) || prefixes.length === 0) {
        return iri;
    }
    for (const prefix of prefixes) {
        if (!prefix?.prefix || !prefix?.substitutedPrefix) {
            continue;
        }
        if (iri.startsWith(prefix.prefix)) {
            return (
                prefix.substitutedPrefix + iri.substring(prefix.prefix.length)
            );
        }
    }
    return iri;
}
