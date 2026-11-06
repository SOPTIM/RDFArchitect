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

package org.rdfarchitect.services.shacl.validation;

import de.soptim.opencgmes.cimvocabcheck.core.schema.SchemaIndex;

import org.apache.jena.graph.Node;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** The namespaces the classes and properties of a schema index are declared in. */
final class KnownNamespaces {

    private final Set<String> namespaces;

    private KnownNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    static KnownNamespaces of(SchemaIndex index) {
        var namespaces = new HashSet<String>();
        Stream.concat(index.allClasses().stream(), index.allProperties().stream())
                .filter(Node::isURI)
                .forEach(term -> namespaces.add(namespaceOf(term)));
        return new KnownNamespaces(namespaces);
    }

    /** Whether some term of the index shares {@code term}'s namespace. */
    boolean declares(Node term) {
        return namespaces.contains(namespaceOf(term));
    }

    /** Up to and including the last {@code #} or {@code /}, which is how CIM namespaces end. */
    private static String namespaceOf(Node term) {
        var uri = term.getURI();
        return uri.substring(0, Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/')) + 1);
    }
}
