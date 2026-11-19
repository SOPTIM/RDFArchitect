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

package org.rdfarchitect.services.update.graph;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;

/** What an import names the schemas it writes. */
@UtilityClass
class GraphUris {

    private final String FALL_BACK_NAME = "graph";

    /**
     * The uri a schema gets.
     *
     * @param requested what the caller asked for, {@code null} or blank to derive one
     * @param fallbackName what to derive it from, a file name or the name of a profile
     */
    String normalize(String requested, String fallbackName) {
        if (requested == null || requested.isBlank()) {
            return fromName(fallbackName);
        }
        var trimmed = requested.trim();
        return trimmed.contains("://") ? trimmed : RDFA.GRAPH_URI + trimmed;
    }

    /** A uri built from a name, with any path, extension and punctuation taken out of it. */
    String fromName(String name) {
        var nonNull = Objects.requireNonNullElse(name, FALL_BACK_NAME);
        var fileNamePath = Paths.get(nonNull).getFileName();
        var lastPathSegment = fileNamePath == null ? FALL_BACK_NAME : fileNamePath.toString();
        var lastDotIndex = lastPathSegment.lastIndexOf(".");
        var sanitized =
                lastPathSegment
                        .substring(0, lastDotIndex < 0 ? lastPathSegment.length() : lastDotIndex)
                        .replaceAll("\\W", "_");
        return RDFA.GRAPH_URI + (sanitized.isBlank() ? FALL_BACK_NAME : sanitized);
    }

    /**
     * The uri as it can be used, given what is taken already. {@code reserved} is added to, so that
     * two schemas of the same import cannot be given the same name either.
     */
    String ensureUnique(String graphUri, Set<String> reserved) {
        var candidate = graphUri;
        var suffix = 1;
        while (reserved.contains(candidate)) {
            candidate = graphUri + "_" + suffix++;
        }
        reserved.add(candidate);
        return candidate;
    }
}
