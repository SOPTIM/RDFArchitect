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

package org.rdfarchitect.services.archive;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GraphFolderNames {

    static final int MAX_LENGTH = 60;

    private static final String FALLBACK = "graph";

    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:(//)?");
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern UNSAFE_ENDS = Pattern.compile("^[-.]+|[-.]+$");

    private static final Pattern RESERVED_NAMES =
            Pattern.compile("(?i)^(con|prn|aux|nul|com\\d|lpt\\d)(\\..*)?$");

    public static Map<String, String> assign(List<String> graphUris) {
        var folders = new LinkedHashMap<String, String>();
        var taken = new HashSet<String>();
        for (var graphUri : graphUris) {
            var base = folderNameOf(graphUri);
            var folder = base;
            for (var counter = 2; !taken.add(folder.toLowerCase(Locale.ROOT)); counter++) {
                var suffix = "-" + counter;
                folder =
                        base.substring(0, Math.min(base.length(), MAX_LENGTH - suffix.length()))
                                + suffix;
            }
            folders.put(graphUri, folder);
        }
        return folders;
    }

    static String folderNameOf(String graphUri) {
        var name = SCHEME.matcher(graphUri).replaceFirst("");
        name = UNSAFE_CHARACTERS.matcher(name).replaceAll("-");
        name = UNSAFE_ENDS.matcher(name).replaceAll("");
        if (name.length() > MAX_LENGTH) {
            name = UNSAFE_ENDS.matcher(name.substring(name.length() - MAX_LENGTH)).replaceAll("");
        }
        if (name.isEmpty()) {
            return FALLBACK;
        }
        return RESERVED_NAMES.matcher(name).matches() ? FALLBACK + "-" + name : name;
    }
}
