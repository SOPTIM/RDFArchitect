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

package org.rdfarchitect.cim.data.queries.update.cimupdates.packages;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMPackage;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.cim.rdf.resources.CIMS;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class CIMUpdatesPackagesTest extends CIMUpdatesTestBase {

    private static final String PACKAGE_FILE_PATH = "packages/package.ttl";
    private static final String PACKAGE_AND_CLASS_FILE_PATH = "packages/package_and_class.ttl";

    private static CIMPackage packageRequired;
    /**
     * New package but with all optionals, meaning comment
     */
    private static CIMPackage packageOptional;

    @BeforeAll
    static void setUpPackageEnvironment() {
        CIMPackage basePackage = CIMPackage.builder()
                                           .uuid(MY_UUID)
                                           .uri(new URI(PACKAGE_URI))
                                           .label(new RDFSLabel(PACKAGE_LABEL, "en"))
                                           .build();
        packageRequired = basePackage.toBuilder().build();
        packageOptional = basePackage.toBuilder()
                                     .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                                     .build();
    }

    @Nested
    class replacePackage {

        @Test
        @DisplayName("Replaces package with new package and updates belongsToCategory information")
        void replacePackage_packageAndClassExists_replacesPackage() {
            //Arrange
            addGraphFromFile(PACKAGE_AND_CLASS_FILE_PATH);
            var otherPackage = packageRequired.toBuilder()
                                              .uri(new URI(OTHER_PACKAGE_URI))
                                              .label(new RDFSLabel(OTHER_PACKAGE_LABEL, "en"))
                                              .build();

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.replacePackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        otherPackage
                                                                       )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(PACKAGE_LABEL, "en"))).isFalse();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_CLASS_URI), CIMS.belongsToCategory.asNode(), NodeFactory.createURI(PACKAGE_URI))).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(OTHER_PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(OTHER_PACKAGE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_CLASS_URI), CIMS.belongsToCategory.asNode(), NodeFactory.createURI(OTHER_PACKAGE_URI))).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class insertPackage {

        @Test
        @DisplayName("Inserts package without comment into empty graph")
        void insertPackage_emptyGraph_addsPackageRequired() {
            //Arrange

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertPackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        packageRequired
                                                                      )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(PACKAGE_LABEL, "en"))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Inserts package with comment into empty graph")
        void insertPackage_emptyGraph_addsPackageOptional() {
            //Arrange

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertPackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        packageOptional
                                                                      )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(PACKAGE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                          .asNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Throws exception if necessary arguments for the new enum entry have not been provided")
        void insertPackage_invalidNewPackage_throwsException() {
            //Arrange
            CIMPackage newPackage = CIMPackage.builder()
                                              .label(new RDFSLabel("invalid", "en"))
                                              .build();

            //Act + Assert
            assertThrows(Exception.class, () -> executeWriteTransaction(graph ->
                                                                                  CIMUpdates.insertPackage(
                                                                                            graph,
                                                                                            databasePort.getPrefixMapping(DATASET_NAME),
                                                                                            newPackage
                                                                                                          )
                                                                       ));
        }

        @Test
        @DisplayName("Insert does nothing if same package without comment exists")
        void insertPackage_packageAlreadyExistsWithoutComment_doesNothing() {
            //Arrange
            addGraphFromFile(PACKAGE_FILE_PATH);

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertPackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        packageRequired
                                                                      )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(PACKAGE_LABEL, "en"))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Adds comment to package if package already exists but without comment")
        void insertPackage_packageAlreadyExistsWithoutCommentAddWithComment_addsComment() {
            //Arrange
            addGraphFromFile(PACKAGE_FILE_PATH);

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertPackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        packageOptional
                                                                      )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), CIMS.classCategory.asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(PACKAGE_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                          .asNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class deletePackage {

        @Test
        @DisplayName("Deletes package from graph")
        void deletePackage_packageExists_deletesPackage() {
            //Arrange
            addGraphFromFile(PACKAGE_FILE_PATH);

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.deletePackage(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        packageRequired.getUuid().toString()
                                                                      )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(PACKAGE_URI), RDF.type.asNode(), Node.ANY)).isFalse();
            } finally {
                testGraph.end();
            }
        }
    }
}
