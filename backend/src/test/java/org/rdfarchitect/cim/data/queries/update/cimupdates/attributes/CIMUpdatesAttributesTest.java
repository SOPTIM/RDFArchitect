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

package org.rdfarchitect.cim.data.queries.update.cimupdates.attributes;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class CIMUpdatesAttributesTest extends CIMUpdatesTestBase {

    private static final String ATTRIBUTE_FILE_PATH = "attributes/attribute.ttl";
    private static final String ATTRIBUTE_OPTIONAL_FILE_PATH = "attributes/attribute_optional.ttl";
    private static final String MULTIPLE_ATTRIBUTES_FILE_PATH = "attributes/multiple_attributes.ttl";
    private static final String MULTIPLE_CLASSES_FILE_PATH = "attributes/multiple_classes.ttl";
    private static final String BLANK_NODE_FIXED_PREDICATE = URI_PREFIX + "fixedPredicate";
    private static final String BLANK_NODE_DEFAULT_PREDICATE = URI_PREFIX + "defaultPredicate";
    private static final String URI_FIXED_VALUE = URI_PREFIX + "fixedValue";

    private static CIMAttribute attributeRequired;
    /**
     * New attribute but with all optionals, meaning comment, isFixed and isDefault
     */
    private static CIMAttribute attributeOptional;
    /**
     * New attribute with only requireds like newAttributeRequired, but with a primitive datatype instead of an unknown one.
     */
    private static CIMAttribute attributePrimitive;
    private static CIMAttribute attributeBlankNodeValues;
    private static CIMAttribute attributeUriValue;

    @BeforeAll
    static void setUpAttributeEnvironment() {
        CIMAttribute baseAttribute = CIMAttribute.builder()
                                                 .uuid(MY_UUID)
                                                 .uri(new URI(ATTRIBUTE_URI))
                                                 .label(new RDFSLabel(ATTRIBUTE_LABEL, "en"))
                                                 .domain(new RDFSDomain(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                                                 .multiplicity(new CIMSMultiplicity(MULTIPLICITY_URI))
                                                 .stereotype(new CIMSStereotype(CIMStereotypes.attribute.getURI()))
                                                 .dataType(new CIMSDataType(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en"), CIMSDataType.Type.UNKNOWN))
                                                 .build();
        attributeRequired = baseAttribute.toBuilder().build();
        attributeOptional = baseAttribute.toBuilder()
                                         .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                                         .fixedValue(new CIMSIsFixed(IS_FIXED_VALUE))
                                         .defaultValue(new CIMSIsDefault(IS_DEFAULT_VALUE))
                                         .build();
        attributePrimitive = baseAttribute.toBuilder()
                                          .dataType(new CIMSPrimitiveDataType(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL, "en")))
                                          .build();
        attributeBlankNodeValues = baseAttribute.toBuilder()
                                                .fixedValue(new CIMSIsFixed(URI_FIXED_VALUE, null, true, new URI(BLANK_NODE_FIXED_PREDICATE), true))
                                                .defaultValue(new CIMSIsDefault(IS_DEFAULT_VALUE, null, true, new URI(BLANK_NODE_DEFAULT_PREDICATE), false))
                                                .build();
        attributeUriValue = baseAttribute.toBuilder()
                                         .fixedValue(new CIMSIsFixed(URI_FIXED_VALUE, null, false, null, true))
                                         .build();
    }

    @Nested
    class replaceAttribute {

        @Test
        @DisplayName("Replaces existing attribute with attribute with new label")
        void replaceAttribute_attributeExistsRequired_replacesAttributeRequired() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributeRequired)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces existing attribute with comment, fixed and default value with attribute with new label")
        void replaceAttribute_attributeExistsOptional_replacesAttributeOptional() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_OPTIONAL_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributeOptional)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                            .asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.isFixed.asNode(), NodeFactory.createLiteralString(IS_FIXED_VALUE))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.isDefault.asNode(), NodeFactory.createLiteralString(IS_DEFAULT_VALUE))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Throws exception if necessary arguments for the new attribute have not been provided")
        void replaceAttribute_invalidNewAttribute_throwsException() {
            //Arrange
            CIMAttribute newAttribute = CIMAttribute.builder()
                                                    .label(new RDFSLabel("invalid", "en"))
                                                    .build();

            //Act + Assert
            assertThrows(Exception.class, () -> executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          newAttribute)
                                                                        ));
        }

        @Test
        @DisplayName("Replaces existing attribute with comment, fixed and default value with attribute with new label")
        void replaceAttribute_attributeExistsPrimitive_replacesAttributePrimitive() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributePrimitive)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.datatype.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces existing attribute with blank node fixed/default values")
        void replaceAttribute_attributeWithBlankNodeValues_insertsBlankNodes() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributeBlankNodeValues)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                var subject = NodeFactory.createURI(ATTRIBUTE_URI);
                var fixedTriples = testGraph.find(subject, CIMS.isFixed.asNode(), Node.ANY).toList();
                var defaultTriples = testGraph.find(subject, CIMS.isDefault.asNode(), Node.ANY).toList();

                assertThat(fixedTriples).hasSize(1);
                assertThat(defaultTriples).hasSize(1);

                var fixedBlank = fixedTriples.get(0).getObject();
                var defaultBlank = defaultTriples.get(0).getObject();

                assertThat(fixedBlank.isBlank()).isTrue();
                assertThat(defaultBlank.isBlank()).isTrue();
                assertThat(testGraph.contains(fixedBlank,
                                              NodeFactory.createURI(BLANK_NODE_FIXED_PREDICATE),
                                              NodeFactory.createURI(URI_FIXED_VALUE))).isTrue();
                assertThat(testGraph.contains(defaultBlank,
                                              NodeFactory.createURI(BLANK_NODE_DEFAULT_PREDICATE),
                                              NodeFactory.createLiteral(IS_DEFAULT_VALUE))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces existing attribute and removes blank node fixed/default values")
        void replaceAttribute_attributeWithBlankNodeValues_removesBlankNodes() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);
            var fixedBlank = NodeFactory.createBlankNode();
            var defaultBlank = NodeFactory.createBlankNode();
            executeWriteTransaction(graph -> {
                graph.add(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), CIMS.isFixed.asNode(), fixedBlank);
                graph.add(fixedBlank, NodeFactory.createURI(BLANK_NODE_FIXED_PREDICATE), NodeFactory.createLiteralString(URI_FIXED_VALUE));
                graph.add(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), CIMS.isDefault.asNode(), defaultBlank);
                graph.add(defaultBlank, NodeFactory.createURI(BLANK_NODE_DEFAULT_PREDICATE), NodeFactory.createLiteralString(IS_DEFAULT_VALUE));
            });

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                databasePort.getPrefixMapping(DATASET_NAME),
                                GRAPH_URI,
                                attributeRequired)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(fixedBlank, Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(defaultBlank, Node.ANY, Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces existing attribute with URI fixed value")
        void replaceAttribute_attributeWithUriValue_insertsUriNode() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributeUriValue)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI),
                                              CIMS.isFixed.asNode(),
                                              NodeFactory.createURI(URI_FIXED_VALUE))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces existing attribute without optionals with attribute with new label and optionals")
        void replaceAttribute_attributeExistsRequired_replacesAttributeWithOptional() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          attributeOptional)
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                            .asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.isFixed.asNode(), NodeFactory.createLiteralString(IS_FIXED_VALUE))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.isDefault.asNode(), NodeFactory.createLiteralString(IS_DEFAULT_VALUE))).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class deleteAttribute {

        @Test
        @DisplayName("Deletes existing attribute from graph")
        void deleteAttribute_attributeExists_deletesAttribute() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.deleteAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID)
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), Node.ANY, Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes nothing if attribute does not exist")
        void deleteAttribute_attributeDoesNotExist_doesNothing() {
            //Arrange
            addGraphFromFile(ATTRIBUTE_FILE_PATH);
            var nonExistingUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.deleteAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          nonExistingUUID)
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(EXISTING_ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                               .toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes existing attribute from graph")
        void deleteAttribute_emptyGraph_doesNothing() {
            //Arrange
            var nonExistingUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.deleteAttribute(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          nonExistingUUID)
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isTrue
                assertThat(testGraph.isEmpty()).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class replacesAttribute {

        @Test
        @DisplayName("Replaces two attributes with two new attributes with different labels")
        void replacesAttributes_attributesExist_replacesAttributesWithDifferentLabels() {
            //Arrange
            addGraphFromFile(MULTIPLE_ATTRIBUTES_FILE_PATH);
            CIMAttribute newAttributeOne = attributeRequired.toBuilder()
                                                            .uri(new URI(ATTRIBUTE_URI))
                                                            .label(new RDFSLabel(ATTRIBUTE_LABEL, "en"))
                                                            .build();

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttributes(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString(),
                                          List.of(newAttributeOne))
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces only the attributes of one class but does not change the other class")
        void replacesAttributes_multipleClassesWithAttributesExist_replacesOnlyAttributesOfProvidedClassUUID() {
            //Arrange
            addGraphFromFile(MULTIPLE_CLASSES_FILE_PATH);

            CIMAttribute newAttributeOne = attributeRequired.toBuilder()
                                                            .uri(new URI(ATTRIBUTE_URI))
                                                            .label(new RDFSLabel(ATTRIBUTE_LABEL, "en"))
                                                            .build();

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttributes(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString(),
                                          List.of(newAttributeOne))
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(OTHER_ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(OTHER_CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(OTHER_CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                            .toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ATTRIBUTE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ATTRIBUTE_URI), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri().toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes attributes of class if no new attributes have been provided")
        void replacesAttributes_attributesExist_deletesAttributes() {
            //Arrange
            addGraphFromFile(MULTIPLE_ATTRIBUTES_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttributes(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString(),
                                          List.of())
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), Node.ANY, Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Does nothing if class is non-existing and no new attributes have been provided")
        void replacesAttributes_classDoesNotExist_doesNothing() {
            //Arrange
            addGraphFromFile(MULTIPLE_ATTRIBUTES_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceAttributes(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          "does not exist",
                                          List.of())
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), RDFS.label.asNode(), NodeFactory.createLiteralLang(EXISTING_ATTRIBUTE_LABEL +
                                                                                                                                                                "A", "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                                     .toNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), RDF.type.asNode(), RDF.Property.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), RDFS.label.asNode(), NodeFactory.createLiteralLang(EXISTING_ATTRIBUTE_LABEL +
                                                                                                                                                                "B", "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), RDFS.domain.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), RDFS.range.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), CIMS.multiplicity.asNode(), new CIMSMultiplicity(MULTIPLICITY_URI).getUri()
                                                                                                                                                                     .toNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class deleteAttributesOfType {

        @Test
        @DisplayName("Deletes all attributes with the provided class URI")
        void deleteAttributesOfType_attributesExist_deletesAttributes() {
            //Arrange
            addGraphFromFile(MULTIPLE_ATTRIBUTES_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.deleteAttributesOfType(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString())
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "A"), Node.ANY, Node.ANY)).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ATTRIBUTE_URI + "B"), Node.ANY, Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }
    }
}
