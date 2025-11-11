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

package org.rdfarchitect.cim.data.queries.update.cimupdates.enumentries;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.RDFType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.data.queries.update.cimupdates.CIMUpdatesTestBase;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class CIMUpdatesEnumEntriesTest extends CIMUpdatesTestBase {

    private static final String ENUMENTRIES_FILE_PATH = "enumentries/enumentries.ttl";
    private static final String ENUM_CLASS_FILE_PATH = "enumentries/enum_class.ttl";

    private static CIMEnumEntry enumEntryRequired;
    /**
     * New enum entry but with all optionals, meaning comment and stereotype
     */
    private static CIMEnumEntry enumEntryOptional;

    @BeforeAll
    static void setUpEnumEntryEnvironment() {
        CIMEnumEntry baseEnumEntry = CIMEnumEntry.builder()
                                                 .uri(new URI(ENUM_ENTRY_URI))
                                                 .type(new RDFType(new URI(CLASS_URI), new RDFSLabel(CLASS_LABEL)))
                                                 .label(new RDFSLabel(ENUM_ENTRY_LABEL, "en"))
                                                 .build();
        enumEntryRequired = baseEnumEntry.toBuilder().build();
        enumEntryOptional = baseEnumEntry.toBuilder()
                                         .comment(new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)))
                                         .stereotype(new CIMSStereotype(CIMStereotypes.enumLiteral.toString()))
                                         .build();
    }

    @Nested
    class insertEnumEntry {

        @Test
        @DisplayName("Inserts enum entry without comment and stereotype into empty graph")
        void insertEnumEntry_emptyGraph_addsEnumEntryRequired() {
            //Arrange

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertEnumEntry(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        enumEntryRequired
                                                                        )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Inserts enum entry with comment and stereotype into empty graph")
        void insertEnumEntry_emptyGraph_addsEnumEntryOptional() {
            //Arrange

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertEnumEntry(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        enumEntryOptional
                                                                        )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                             .asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), CIMS.stereotype.asNode(), CIMStereotypes.enumLiteral.asNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Throws exception if necessary arguments for the new enum entry have not been provided")
        void insertEnumEntry_invalidNewEnumEntry_throwsException() {
            //Arrange
            CIMEnumEntry newEnumEntry = CIMEnumEntry.builder()
                                                    .label(new RDFSLabel("invalid", "en"))
                                                    .build();

            //Act + Assert
            assertThrows(Exception.class, () -> executeWriteTransaction(graph ->
                                                                                  CIMUpdates.insertEnumEntry(
                                                                                            graph,
                                                                                            databasePort.getPrefixMapping(DATASET_NAME),
                                                                                            newEnumEntry
                                                                                                            )
                                                                       ));
        }

        @Test
        @DisplayName("Insert does nothing if same enum entry without comment and stereotype exists")
        void insertEnumEntry_enumEntryExistsWithRequired_doesNothing() {
            //Arrange
            addGraphFromFile(ENUMENTRIES_FILE_PATH);

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertEnumEntry(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        enumEntryRequired
                                                                        )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Adds comment and stereotype to enum entry if it exists without comment and stereotype")
        void insertEnumEntry_enumEntryExistsWithRequired_addsOptionals() {
            //Arrange
            addGraphFromFile(ENUMENTRIES_FILE_PATH);

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertEnumEntry(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        enumEntryOptional
                                                                        )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                             .asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), CIMS.stereotype.asNode(), CIMStereotypes.enumLiteral.asNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }

        @Test
        @DisplayName("Adds enum entry with comment and correct stereotype if invalid stereotype has been provided")
        void insertEnumEntry_emptyGraphInvalidStereotypeGiven_insertsWithCorrectStereotype() {
            //Arrange
            CIMEnumEntry newEnumEntry = enumEntryOptional.toBuilder()
                                                         .stereotype(new CIMSStereotype("invalid"))
                                                         .build();
            addGraphFromFile(ENUMENTRIES_FILE_PATH);
            addTriple(NodeFactory.createURI(newEnumEntry.getUri().toString()), CIMS.stereotype.asNode(), NodeFactory.createURI("invalid"));

            //Act
            executeWriteTransaction(graph ->
                                              CIMUpdates.insertEnumEntry(
                                                        graph,
                                                        databasePort.getPrefixMapping(DATASET_NAME),
                                                        newEnumEntry
                                                                        )
                                   );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.comment.asNode(), new RDFSComment(COMMENT, new URI(COMMENT_FORMAT)).asTypedLiteral()
                                                                                                                                                             .asNode())).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), CIMS.stereotype.asNode(), CIMStereotypes.enumLiteral.asNode())).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }

    @Nested
    class replaceEnumEntries {

        @Test
        @DisplayName("Replaces enum entries of a enum class with new enum entries")
        void replaceEnumEntries_enumClassAndEntriesExist_replacesEnumEntries() {
            //Arrange
            addGraphFromFile(ENUM_CLASS_FILE_PATH);

            //Act
            executeUpdateOnTestGraph(
                      CIMUpdates.replaceEnumEntries(
                                          databasePort.getPrefixMapping(DATASET_NAME),
                                          GRAPH_URI,
                                          MY_UUID.toString(),
                                          List.of(enumEntryRequired))
                                .build()
                                    );

            //Assert
            try {
                testGraph.begin(TxnType.READ);
                //isFalse
                assertThat(testGraph.contains(NodeFactory.createURI(EXISTING_ENUM_ENTRY_URI), Node.ANY, Node.ANY)).isFalse();
                //isTrue
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDF.type.asNode(), NodeFactory.createURI(CLASS_URI))).isTrue();
                assertThat(testGraph.contains(NodeFactory.createURI(ENUM_ENTRY_URI), RDFS.label.asNode(), NodeFactory.createLiteralLang(ENUM_ENTRY_LABEL, "en"))).isTrue();
            } finally {
                testGraph.end();
            }
        }
    }
}