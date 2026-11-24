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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Translates between the name a graph is known by and the IRI of the named graph it is stored
 * under. The IRI never leaves the backend: it only exists because RDF stores, SPARQL and the
 * dataset formats need one.
 */
@UtilityClass
public class SchemaIri {

    public static final String PREFIX = "urn:rdfa:schema:";

    /**
     * Returns the IRI of the named graph a graph called {@code name} is stored under.
     *
     * @param name the graph name
     * @return {@code urn:rdfa:schema:} followed by the percent-encoded name
     */
    public static String of(String name) {
        return PREFIX + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Returns the graph name for the IRI of a stored named graph. An IRI made by {@link
     * #of(String)} gives back its name; any other IRI was stored before graphs were named this way
     * and is its own name.
     *
     * @param iri the IRI of a named graph
     * @return the graph name
     */
    public static String toName(String iri) {
        if (iri.startsWith(PREFIX)) {
            return URLDecoder.decode(iri.substring(PREFIX.length()), StandardCharsets.UTF_8);
        }
        var trimmed = iri;
        while (trimmed.endsWith("/") || trimmed.endsWith("#")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        var cut = Math.max(trimmed.lastIndexOf('#'), trimmed.lastIndexOf('/'));
        if (cut < 0) {
            cut = trimmed.lastIndexOf(':');
        }
        var localName = trimmed.substring(cut + 1);
        return localName.isEmpty() ? iri : localName;
    }
}
