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

package org.rdfarchitect.models.changelog;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One named value as a commit found it and as it left it.
 *
 * <p>What a participant that holds settings rather than triples can show of itself. A namespace
 * prefix and the colour a schema is drawn in are both a name with a value behind it, so the
 * changelog can open them up the same way it opens up a graph's triples.
 *
 * @param key what the value is called — the prefix, the schema the colour belongs to
 * @param before what it was, or {@code null} if the commit brought it into existence
 * @param after what it became, or {@code null} if the commit removed it
 */
public record ValueChange(String key, String before, String after) {

    /**
     * Returns what changed between two sets of named values, in a stable order.
     *
     * @param before the values the commit started from
     * @param after the values it produced
     * @return one entry per value that was added, removed or given another value
     */
    public static List<ValueChange> between(Map<String, String> before, Map<String, String> after) {
        return Stream.concat(before.keySet().stream(), after.keySet().stream())
                .distinct()
                .sorted()
                .filter(key -> !Objects.equals(before.get(key), after.get(key)))
                .map(key -> new ValueChange(key, before.get(key), after.get(key)))
                .toList();
    }
}
