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

package org.rdfarchitect.services.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.update.classes.UniqueLabelFactory;

import java.util.Set;

class UniqueLabelFactoryTest {

    private static final String PREFIX = "http://example.org#";

    private final Graph graph = GraphFactory.createDefaultGraph();

    @ParameterizedTest
    @ValueSource(strings = {"MyClass", "My Class", "My\"Class", "123", "true", "rdf:type"})
    void uniqueClassLabel_labelIsTaken_appendsCopy(String label) {
        addClass(label);

        var uniqueLabel = UniqueLabelFactory.uniqueClassLabel(graph, label(label), Set.of());

        assertThat(uniqueLabel.getValue()).isEqualTo(label + "-Copy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MyClass", "123", "true", "rdf:type"})
    void uniqueClassLabel_labelIsFree_keepsIt(String label) {
        addClass("otherClass");

        var uniqueLabel = UniqueLabelFactory.uniqueClassLabel(graph, label(label), Set.of());

        assertThat(uniqueLabel.getValue()).isEqualTo(label);
    }

    @Test
    void uniqueClassLabel_labelIsOnlyTakenByALabelOfThisBatch_appendsCopy() {
        var uniqueLabel = UniqueLabelFactory.uniqueClassLabel(graph, label("123"), Set.of("123"));

        assertThat(uniqueLabel.getValue()).isEqualTo("123-Copy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "123", "true"})
    void existingAssociationLabels_labelIsTaken_findsIt(String label) {
        var domainUri = new URI(PREFIX + "MyClass");
        addAssociation(domainUri, label);

        var existingLabels =
                UniqueLabelFactory.existingAssociationLabels(graph, domainUri, label(label));

        assertThat(existingLabels).containsExactly(label);
    }

    private RDFSLabel label(String value) {
        return new RDFSLabel(value, "en");
    }

    private void addClass(String label) {
        var classNode = NodeFactory.createURI(PREFIX + "class" + label.hashCode());
        graph.add(classNode, RDF.type.asNode(), RDFS.Class.asNode());
        graph.add(classNode, RDFS.label.asNode(), label(label).asLangLiteral().asNode());
    }

    private void addAssociation(URI domainUri, String label) {
        var associationNode = NodeFactory.createURI(domainUri + "." + label);
        graph.add(associationNode, RDF.type.asNode(), RDF.Property.asNode());
        graph.add(
                associationNode, RDFS.domain.asNode(), NodeFactory.createURI(domainUri.toString()));
        graph.add(associationNode, RDFS.label.asNode(), label(label).asLangLiteral().asNode());
    }
}
