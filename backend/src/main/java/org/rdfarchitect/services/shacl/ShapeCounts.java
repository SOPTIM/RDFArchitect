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

package org.rdfarchitect.services.shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a constraints document holds, counted the way SHACL defines its parts.
 *
 * <p>A triple count says how big a document is but not what is in it: 9,931 triples is 145 node
 * shapes and 572 rules in one official file and something else entirely in another.
 *
 * @param nodeShapes subjects typed {@code sh:NodeShape}, or with a target, that are not property
 *     shapes
 * @param propertyShapes subjects typed {@code sh:PropertyShape} and every value of {@code
 *     sh:property}, inline ones included
 * @param sparqlConstraints distinct values of {@code sh:sparql}
 */
public record ShapeCounts(int nodeShapes, int propertyShapes, int sparqlConstraints) {

    private static final List<Node> TARGETS =
            List.of(
                    SHACLM.targetClass.asNode(),
                    SHACLM.targetNode.asNode(),
                    SHACLM.targetSubjectsOf.asNode(),
                    SHACLM.targetObjectsOf.asNode());

    /** Must be called inside a transaction on whatever holds {@code shapes}. */
    public static ShapeCounts of(Graph shapes) {
        var propertyShapes = new HashSet<Node>();
        shapes.find(Node.ANY, RDF.type.asNode(), SHACLM.PropertyShape.asNode())
                .forEachRemaining(triple -> propertyShapes.add(triple.getSubject()));
        shapes.find(Node.ANY, SHACLM.property.asNode(), Node.ANY)
                .filterDrop(triple -> triple.getObject().isLiteral())
                .forEachRemaining(triple -> propertyShapes.add(triple.getObject()));

        var nodeShapes = new HashSet<Node>();
        shapes.find(Node.ANY, RDF.type.asNode(), SHACLM.NodeShape.asNode())
                .forEachRemaining(triple -> nodeShapes.add(triple.getSubject()));
        for (var target : TARGETS) {
            shapes.find(Node.ANY, target, Node.ANY)
                    .forEachRemaining(triple -> nodeShapes.add(triple.getSubject()));
        }
        nodeShapes.removeAll(propertyShapes);

        Set<Node> sparql = new HashSet<>();
        shapes.find(Node.ANY, SHACLM.sparql.asNode(), Node.ANY)
                .forEachRemaining(triple -> sparql.add(triple.getObject()));

        return new ShapeCounts(nodeShapes.size(), propertyShapes.size(), sparql.size());
    }
}
