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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.GraphFactory;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
/**
 * Layout IRIs such as {@code _<uuid>} are relative, so a parser would resolve them against its
 * base. The archive stores them absolute against a fixed one.
 */
public final class LayoutIris {

    static final String BASE = "urn:rdfarchitect:layout:";

    private static final Pattern ABSOLUTE_IRI = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

    public static Graph toArchived(Graph layout) {
        return mapIris(layout, iri -> ABSOLUTE_IRI.matcher(iri).matches() ? iri : BASE + iri);
    }

    private static Graph mapIris(Graph source, UnaryOperator<String> mapping) {
        var result = GraphFactory.createDefaultGraph();
        result.getPrefixMapping().setNsPrefixes(source.getPrefixMapping());
        source.find()
                .forEach(
                        triple ->
                                result.add(
                                        Triple.create(
                                                map(triple.getSubject(), mapping),
                                                map(triple.getPredicate(), mapping),
                                                map(triple.getObject(), mapping))));
        return result;
    }

    private static Node map(Node node, UnaryOperator<String> mapping) {
        return node.isURI() ? NodeFactory.createURI(mapping.apply(node.getURI())) : node;
    }
}
