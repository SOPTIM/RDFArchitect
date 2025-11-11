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

package org.rdfarchitect.cim.data.queries.update.cimupdates.associations;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMAssociation;
import org.rdfarchitect.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.cim.rdf.resources.CIMS;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class CIMUpdatesAssociationsTest extends CIMUpdatesTestBase {

    private static final String ASSOCIATIONS_FILE_PATH = "associations/associations.ttl";
    private static final UUID MY_UUID = UUID.fromString("43836908-c7f7-4749-bb8b-3ac9250de655");
    private static final String MULTIPLICITY_URI = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:1..1";

    private static CIMAssociation associationRequired;
    private static CIMAssociation associationInverseRequired;

    @BeforeAll
    static void setUpAssociationsEnvironment() {
        CIMAssociation baseAssociation = CIMAssociation.builder()
                                                       .uuid(ASSOC_UUID)
                                                       .uri(new URI(ASSOC_URI))
                                                       .label(new RDFSLabel(INVERSE_LABEL, "en"))
                                                       .domain(new RDFSDomain(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                                                       .range(new RDFSRange(new URI(INVERSE_URI), new RDFSLabel(INVERSE_LABEL, "en")))
                                                       .associationUsed(new CIMSAssociationUsed("Yes"))
                                                       .inverseRoleName(new CIMSInverseRoleName(new URI(INVERSE_ASSOC_URI)))
                                                       .multiplicity(new CIMSMultiplicity(MULTIPLICITY_URI))
                                                       .build();
        CIMAssociation baseInverseAssociation = CIMAssociation.builder()
                                                              .uuid(INVERSE_ASSOC_UUID)
                                                              .uri(new URI(INVERSE_ASSOC_URI))
                                                              .label(new RDFSLabel(CLASS_LABEL, "en"))
                                                              .domain(new RDFSDomain(new URI(INVERSE_URI), new RDFSLabel(INVERSE_LABEL, "en")))
                                                              .range(new RDFSRange(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                                                              .associationUsed(new CIMSAssociationUsed("Yes"))
                                                              .inverseRoleName(new CIMSInverseRoleName(new URI(ASSOC_URI)))
                                                              .multiplicity(new CIMSMultiplicity(MULTIPLICITY_URI))
                                                              .build();
        associationRequired = baseAssociation.toBuilder().build();
        associationInverseRequired = baseInverseAssociation.toBuilder().build();
    }

    @Nested
    class deleteAssociation {

        @Test
        @DisplayName("Deletes existing association (and inverse) from graph")
        void deleteAssociation_associationAndInverseExist_deletesAssociations() {
            //Arrange
            addGraphFromFile(ASSOCIATIONS_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.deleteAssociation(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          ASSOC_UUID)
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_INVERSE_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class replaceAssociation {

        @Test
        @DisplayName("Replaces an existing association (and inverse) with a different association")
        void replaceAssociation_associationAndInverseExist_replacesAssociation() {
            //Arrange
            addGraphFromFile(ASSOCIATIONS_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAssociation(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          new CIMAssociationPair(associationRequired, associationInverseRequired))
                                .build()
                                    );
            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_INVERSE_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(INVERSE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.range.asNode(), NodeFactory.createURI(INVERSE_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.associationUsed.asNode(), NodeFactory.createLiteralString("Yes"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.inverseRoleName.asNode(), NodeFactory.createURI(INVERSE_ASSOC_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(CLASS_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.domain.asNode(), NodeFactory.createURI(INVERSE_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.associationUsed.asNode(), NodeFactory.createLiteralString("Yes"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.inverseRoleName.asNode(), NodeFactory.createURI(ASSOC_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                          .toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class replaceAssociations {

        @Test
        @DisplayName("Replaces all existing associations (and inverses) with different associations")
        void replaceAssociations_associationAndInverseExist_replacesAssociations() {
            //Arrange
            addGraphFromFile(ASSOCIATIONS_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAssociations(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString(),
                                          List.of(new CIMAssociationPair(associationRequired, associationInverseRequired)))
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_INVERSE_ASSOC_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(INVERSE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), RDFS.range.asNode(), NodeFactory.createURI(INVERSE_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.associationUsed.asNode(), NodeFactory.createLiteralString("Yes"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.inverseRoleName.asNode(), NodeFactory.createURI(INVERSE_ASSOC_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ASSOC_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(CLASS_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.domain.asNode(), NodeFactory.createURI(INVERSE_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.associationUsed.asNode(), NodeFactory.createLiteralString("Yes"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.inverseRoleName.asNode(), NodeFactory.createURI(ASSOC_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(INVERSE_ASSOC_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                          .toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }
}