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

package org.rdfarchitect.cim.data.queries.update.cimupdates.classes;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;

import java.util.List;
import java.util.UUID;

public class CIMUpdatesClassesTest extends CIMUpdatesTestBase {

    private static final String CLASS_AND_SUBCLASS_FILE_PATH = "classes/class_and_subclass.ttl";
    private static final String CLASS_OPTIONAL_FILE_PATH = "classes/class_optional.ttl";
    private static final String ENUM_FILE_PATH = "classes/enum.ttl";
    private static final String MULTIPLE_CLASSES_FILE_PATH = "classes/multiple_classes.ttl";

    private static CIMClass classRequired;

    /**
     * New class but with all optionals, meaning subClassOf, comment, belongsToCategory and
     * stereotype
     */
    private static CIMClass classOptional;

    @BeforeAll
    static void setUpClassEnvironment() {
        CIMClass baseClass =
                CIMClass.builder()
                        .uuid(MY_UUID)
                        .uri(new URI(CLASS_URI))
                        .label(new RDFSLabel(CLASS_LABEL, "en"))
                        .build();
        classRequired = baseClass.toBuilder().build();
        classOptional =
                baseClass.toBuilder()
                        .uuid(MY_UUID)
                        .superClass(
                                new RDFSSubClassOf(
                                        new URI(SUPER_CLASS_URI),
                                        new RDFSLabel(SUPER_CLASS_LABEL, "en")))
                        .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                        .belongsToCategory(
                                new CIMSBelongsToCategory(
                                        new URI(PACKAGE_URI),
                                        new RDFSLabel(PACKAGE_LABEL),
                                        UUID.randomUUID()))
                        .stereotypes(
                                List.of(new CIMSStereotype(CIMStereotypes.concrete.toString())))
                        .build();
    }

    @Nested
    class replaceClass {

        @Test
        @DisplayName("Replaces class with new class and updates sub class references")
        void replaceClass_classExists_replacesClassUpdatesReferences() {
            // Arrange
            addGraphFromFile(CLASS_AND_SUBCLASS_FILE_PATH);
            addTriple(
                    NodeFactory.createURI(SUB_CLASS_URI),
                    RDFS.subClassOf.asNode(),
                    NodeFactory.createURI(EXISTING_CLASS_URI));

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.replaceClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    new CIMClassUMLAdapted(classRequired)));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(SUB_CLASS_URI),
                                        RDFS.subClassOf.asNode(),
                                        NodeFactory.createURI(EXISTING_CLASS_URI)))
                        .isFalse();
                // isTrue
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDF.type.asNode(),
                                        RDFS.Class.asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.label.asNode(),
                                        NodeFactory.createLiteralLang(CLASS_LABEL, "en")))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(SUB_CLASS_URI),
                                        RDFS.subClassOf.asNode(),
                                        NodeFactory.createURI(CLASS_URI)))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Replaces class with new class with optionals")
        void replaceClass_classExists_replacesClassOptionals() {
            // Arrange
            addGraphFromFile(CLASS_AND_SUBCLASS_FILE_PATH);

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.replaceClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    new CIMClassUMLAdapted(classOptional)));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
                // isTrue
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDF.type.asNode(),
                                        RDFS.Class.asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.label.asNode(),
                                        NodeFactory.createLiteralLang(CLASS_LABEL, "en")))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.subClassOf.asNode(),
                                        NodeFactory.createURI(SUPER_CLASS_URI)))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.comment.asNode(),
                                        new RDFSComment(COMMENT, new URI(COMMENT_FORMAT))
                                                .asTypedLiteral()
                                                .asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        CIMS.belongsToCategory.asNode(),
                                        NodeFactory.createURI(PACKAGE_URI)))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        CIMS.stereotype.asNode(),
                                        CIMStereotypes.concrete.asNode()))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class insertClass {

        @Test
        @DisplayName("Inserts new class into empty graph")
        void insertClass_emptyGraph_insertsClass() {
            // Arrange

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.insertClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    classRequired));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isTrue
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDF.type.asNode(),
                                        RDFS.Class.asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.label.asNode(),
                                        NodeFactory.createLiteralLang(CLASS_LABEL, "en")))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Inserts new class with optionals into empty graph")
        void insertClass_emptyGraph_insertsClassOptionals() {
            // Arrange

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.insertClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    classOptional));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isTrue
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDF.type.asNode(),
                                        RDFS.Class.asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.label.asNode(),
                                        NodeFactory.createLiteralLang(CLASS_LABEL, "en")))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.subClassOf.asNode(),
                                        NodeFactory.createURI(SUPER_CLASS_URI)))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        RDFS.comment.asNode(),
                                        new RDFSComment(COMMENT, new URI(COMMENT_FORMAT))
                                                .asTypedLiteral()
                                                .asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        CIMS.belongsToCategory.asNode(),
                                        NodeFactory.createURI(PACKAGE_URI)))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(CLASS_URI),
                                        CIMS.stereotype.asNode(),
                                        CIMStereotypes.concrete.asNode()))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class deleteClass {

        @Test
        @DisplayName("Deletes class from graph")
        void deleteClass_classExists_deletesClass() {
            // Arrange
            addGraphFromFile(CLASS_AND_SUBCLASS_FILE_PATH);

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.deleteClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    MY_UUID.toString()));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes class with optionals from graph")
        void deleteClass_classExists_deletesClassOptionals() {
            // Arrange
            addGraphFromFile(CLASS_OPTIONAL_FILE_PATH);

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.deleteClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    MY_UUID.toString()));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes enum class with enum entries from graph")
        void deleteClass_enumClassAndEnumEntriesExist_deletesEnumClassAndEnumEntries() {
            // Arrange
            addGraphFromFile(ENUM_FILE_PATH);

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.deleteClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    MY_UUID.toString()));

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_ENUM_ENTRY_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes class but keeps subClassOf reference and UUID")
        void deleteClass_classAndReferenceExists_deletesClassAndKeepsReference() {
            // Arrange
            addGraphFromFile(CLASS_AND_SUBCLASS_FILE_PATH);
            addTriple(
                    NodeFactory.createURI(SUB_CLASS_URI),
                    RDFS.subClassOf.asNode(),
                    NodeFactory.createURI(EXISTING_CLASS_URI));

            // Act
            executeWriteTransaction(
                    graph ->
                            CIMUpdates.deleteClass(
                                    graph,
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    MY_UUID.toString()));

            // Assert
            var model = ModelFactory.createModelForGraph(testGraph);
            var classResource = model.createResource(EXISTING_CLASS_URI);
            try {
                testGraph.begin(TxnType.READ);
                // only the uuid triple remains for the deleted class
                assertThat(model.listStatements(classResource, null, (RDFNode) null).toList())
                        .hasSize(1)
                        .allMatch(stmt -> stmt.getPredicate().equals(RDFA.uuid));
                // subClassOf reference is kept
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(SUB_CLASS_URI),
                                        RDFS.subClassOf.asNode(),
                                        NodeFactory.createURI(EXISTING_CLASS_URI)))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class deleteBase {

        @Test
        @DisplayName("Deletes class base from graph")
        void deleteBase_classExists_deletesClassBase() {
            // Arrange

            // Act
            executeUpdateOnTestGraph(
                    CIMUpdates.deleteBase(
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    GRAPH_URI,
                                    MY_UUID.toString())
                            .build());

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(EXISTING_CLASS_URI),
                                        Node.ANY,
                                        Node.ANY))
                        .isFalse();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Deletes class base from graph, other class is untouched")
        void deleteBase_twoClassExists_deletesClassBaseOtherClassUntouched() {
            // Arrange
            addGraphFromFile(MULTIPLE_CLASSES_FILE_PATH);

            // Act
            executeUpdateOnTestGraph(
                    CIMUpdates.deleteBase(
                                    databasePort.getPrefixMapping(DATASET_NAME),
                                    GRAPH_URI,
                                    MY_UUID.toString())
                            .build());

            // Assert
            try {
                testGraph.begin(TxnType.READ);
                // isFalse
                var model = ModelFactory.createModelForGraph(testGraph);
                var classResource = model.createResource(EXISTING_CLASS_URI);

                assertThat(
                                model.listStatements(classResource, null, (RDFNode) null)
                                        .filterKeep(stmt -> !stmt.getPredicate().equals(RDFA.uuid))
                                        .hasNext())
                        .isFalse();
                // isTrue
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(OTHER_CLASS_URI),
                                        RDF.type.asNode(),
                                        RDFS.Class.asNode()))
                        .isTrue();
                assertThat(
                                testGraph.contains(
                                        NodeFactory.createURI(OTHER_CLASS_URI),
                                        RDFS.label.asNode(),
                                        NodeFactory.createLiteralLang(OTHER_CLASS_LABEL, "en")))
                        .isTrue();
            } finally {
                testGraph.end();
            }
        }
    }
}
