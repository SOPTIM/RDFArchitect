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

package org.rdfarchitect.database;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.FileSystem;
import org.rdfarchitect.exception.graph.InvalidSchemaNameException;

import java.util.Collection;

/**
 * The rules a graph name has to follow. A graph is exported to a file named after it, so its name
 * has to be a file name on every system the file may end up on; and it is part of every API path,
 * which rules out {@code ;} and the dot segments on top.
 */
@UtilityClass
public class SchemaNames {

    /**
     * The longest name a graph may have. It stays well below the 255 characters a file name may
     * have, so the name still fits once an export appends {@code -shacl.jsonld} or an import
     * appends a number to make it unique.
     */
    public static final int MAX_LENGTH = 200;

    /**
     * Returns {@code name} without surrounding whitespace, provided that is a valid graph name.
     *
     * @param name the name as given
     * @return the name to store the graph under
     * @throws InvalidSchemaNameException if the name is no valid graph name
     */
    public static String validate(String name) {
        if (name == null) {
            throw new InvalidSchemaNameException("");
        }
        var trimmed = name.strip();
        if (trimmed.length() > MAX_LENGTH
                || trimmed.contains(";")
                || trimmed.equals(".")
                || trimmed.equals("..")
                || !FileSystem.WINDOWS.isLegalFileName(trimmed)) {
            throw new InvalidSchemaNameException(trimmed);
        }
        return trimmed;
    }

    /**
     * Returns {@code name}, or the first of {@code name-2}, {@code name-3}, ... that is not in
     * {@code takenNames}.
     *
     * @param name the wanted name
     * @param takenNames the names already in use
     * @return a name not in {@code takenNames}
     */
    public static String unique(String name, Collection<String> takenNames) {
        var candidate = name;
        var suffix = 2;
        while (takenNames.contains(candidate)) {
            candidate = name + "-" + suffix++;
        }
        return candidate;
    }
}
