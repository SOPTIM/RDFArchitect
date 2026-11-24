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

import org.rdfarchitect.database.SchemaNames;

import java.util.Objects;
import java.util.Set;

/** What an import names the schemas it writes. */
@UtilityClass
class ImportedSchemaNames {

    private final String FALL_BACK_NAME = "graph";

    /**
     * Returns the name a schema gets. A name the user asked for has to be valid as it is. One taken
     * from a file name is made valid instead: the entries of a zip cannot be renamed before they
     * are imported.
     *
     * @param requested what the caller asked for, {@code null} or blank to derive one
     * @param fallbackName what to derive it from, a file name or the name of a profile
     * @return a valid schema name
     * @throws org.rdfarchitect.exception.graph.InvalidSchemaNameException if {@code requested} is
     *     no valid schema name
     */
    String normalize(String requested, String fallbackName) {
        if (requested == null || requested.isBlank()) {
            return SchemaNames.fromFileName(baseNameOf(fallbackName), FALL_BACK_NAME);
        }
        return SchemaNames.validate(requested);
    }

    /**
     * Returns the name as it can be used, given what is taken already. {@code reserved} is added
     * to, so that two schemas of the same import cannot be given the same name either.
     *
     * @param schemaName the wanted name
     * @param reserved the names already in use
     * @return a name that was not in {@code reserved}
     */
    String ensureUnique(String schemaName, Set<String> reserved) {
        var candidate = SchemaNames.unique(schemaName, reserved);
        reserved.add(candidate);
        return candidate;
    }

    private String baseNameOf(String fileName) {
        var name = Objects.requireNonNullElse(fileName, FALL_BACK_NAME);
        // Not Paths.get: a name such as a:b.ttl is no valid path on Windows, but a valid file
        // name elsewhere, and the zip entry may well come from there.
        var lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        var lastPathSegment = name.substring(lastSeparator + 1);
        var lastDotIndex = lastPathSegment.lastIndexOf(".");
        var baseName =
                lastPathSegment.substring(
                        0, lastDotIndex < 0 ? lastPathSegment.length() : lastDotIndex);
        return baseName.isBlank() ? FALL_BACK_NAME : baseName;
    }
}
