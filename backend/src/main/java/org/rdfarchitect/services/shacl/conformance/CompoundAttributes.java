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

package org.rdfarchitect.services.shacl.conformance;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads the schema's compound attributes the way the official constraints state them.
 *
 * <p>A {@code «Compound»} attribute, such as {@code Location.mainAddress}, holds a nested
 * structure: in instance data, a blank node of the compound class. The generator states such an
 * attribute as an {@code rdf:XMLLiteral}, which every official file contradicts, so the comparison
 * reads it as a blank node of its class instead, keeping the multiplicity.
 */
final class CompoundAttributes {

    private static final Node COMPOUND =
            NodeFactory.createLiteralString(CIMStereotypes.compoundString);

    private CompoundAttributes() {}

    static Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> asBlankNodes(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> implied,
            Graph ontology) {
        var compoundOf = new HashMap<String, String>();
        ontology.stream(Node.ANY, CIMS.datatype.asNode(), Node.ANY)
                .filter(triple -> triple.getSubject().isURI() && triple.getObject().isURI())
                .filter(
                        triple ->
                                ontology.contains(
                                        triple.getObject(), CIMS.stereotype.asNode(), COMPOUND))
                .forEach(
                        triple ->
                                compoundOf.put(
                                        triple.getSubject().getURI(), triple.getObject().getURI()));
        if (compoundOf.isEmpty()) {
            return implied;
        }
        var read = new LinkedHashMap<EffectiveConstraints.Key, EffectiveConstraints.Constraint>();
        implied.forEach(
                (key, constraint) -> {
                    var compound = compoundOf.get(key.path());
                    read.put(
                            key,
                            compound == null
                                    ? constraint
                                    : new EffectiveConstraints.Constraint(
                                            constraint.minCount(),
                                            constraint.maxCount(),
                                            Set.of(),
                                            Set.of(compound),
                                            null,
                                            Set.of(SHACL.BlankNode.getURI()),
                                            null));
                });
        return read;
    }
}
