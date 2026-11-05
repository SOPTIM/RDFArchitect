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

package org.rdfarchitect.xmi.read;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The namespace prefix table an export states as tagged values, while it is being read.
 *
 * <p>The table comes in pairs keyed by the name behind the colon, {@code nsprefix:cim="cim"} beside
 * {@code nsuri:cim="http://iec.ch/TC57/CIM100#"}. Both halves are needed and they arrive in no
 * particular order, so the two are collected apart and paired once the file has been read.
 *
 * <p>Where the pairs sit is not where they belong. CGMES 3.0 hangs all fourteen of them on a single
 * ordinary class named {@code Configuration_Resource}, which is part of no profile; CGMES 2.4.15
 * states no table at all and its namespaces have to come from elsewhere. They are therefore taken
 * wherever they occur and kept for the export as a whole rather than for whatever carried them.
 */
@Getter
final class XmiPrefixTable {

    private static final String PREFIX_TAG = "nsprefix:";
    private static final String NAMESPACE_TAG = "nsuri:";

    private final Map<String, String> prefixesByKey = new LinkedHashMap<>();
    private final Map<String, String> namespacesByKey = new LinkedHashMap<>();

    /**
     * Takes a tagged value in when it is part of the prefix table.
     *
     * @return whether it was, and is therefore not a tag of whatever carries it
     */
    boolean collect(String name, String value) {
        if (name.startsWith(PREFIX_TAG)) {
            prefixesByKey.put(name.substring(PREFIX_TAG.length()), value);
            return true;
        }
        if (name.startsWith(NAMESPACE_TAG)) {
            namespacesByKey.put(name.substring(NAMESPACE_TAG.length()), value);
            return true;
        }
        return false;
    }

    /**
     * The bindings both halves arrived for, prefix to namespace. A key missing either half states
     * nothing usable and is left out rather than guessed at from the key.
     */
    Map<String, String> paired() {
        var paired = new LinkedHashMap<String, String>();
        prefixesByKey.forEach(
                (key, prefix) -> {
                    var namespace = namespacesByKey.get(key);
                    if (namespace != null) {
                        paired.put(prefix, namespace);
                    }
                });
        return paired;
    }
}
