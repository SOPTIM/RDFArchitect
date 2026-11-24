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
import java.util.Locale;

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

    /** What a character of a file name that a graph name must not hold is replaced by. */
    private static final char REPLACEMENT = '_';

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
     * Turns the name of a file into a valid graph name. A file that is a valid file name on Linux
     * or macOS may still hold a character a graph name must not, or be a name Windows reserves; it
     * is imported anyway, under a name with each such character replaced by {@code _} and a {@code
     * _} appended to a reserved name ({@code CON} becomes {@code CON_}).
     *
     * @param fileName the file name without its extension
     * @param fallback the name to take when nothing usable is left of {@code fileName}
     * @return a valid graph name
     */
    public static String fromFileName(String fileName, String fallback) {
        if (fileName == null || fileName.isBlank()) {
            return fallback;
        }
        var name =
                FileSystem.WINDOWS
                        .toLegalFileName(fileName.strip(), REPLACEMENT)
                        .replace(';', REPLACEMENT);
        name = unreserved(name);
        name = truncated(name).strip();
        try {
            return validate(name);
        } catch (InvalidSchemaNameException _) {
            return fallback;
        }
    }

    /**
     * Appends {@code _} to the part of a reserved name before its first dot. Windows reserves
     * {@code CON} however it is cased, and with any extension.
     */
    private static String unreserved(String name) {
        if (!FileSystem.WINDOWS.isReservedFileName(name.toUpperCase(Locale.ROOT))) {
            return name;
        }
        var dot = name.indexOf('.');
        var end = dot < 0 ? name.length() : dot;
        return name.substring(0, end) + REPLACEMENT + name.substring(end);
    }

    /** Cuts {@code name} to {@link #MAX_LENGTH} characters, without splitting a surrogate pair. */
    private static String truncated(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        var end = MAX_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end);
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
