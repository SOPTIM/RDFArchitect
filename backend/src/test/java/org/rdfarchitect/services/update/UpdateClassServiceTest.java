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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.ChangeLogService;
import org.rdfarchitect.services.update.classes.UpdateClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static utils.TestUtils.*;

@SpringBootTest
class UpdateClassServiceTest {

    private UpdateClassService updateClassService;
    private DatabasePort databasePort;
    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    @Autowired
    private ClassUMLAdaptedMapper classMapper;
    @Autowired
    private PackageMapper packageMapper;

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl());
        var mockChangeLogService = mock(ChangeLogService.class);
        updateClassService = new UpdateClassService(databasePort, classMapper, packageMapper, mockChangeLogService);
        var file = readMultipartFileFromFile(PATH, "class.ttl");
        databasePort.createGraph(graphIdentifier, file);
    }

    @Test
    void addClass_createsNewClass() {
        var packageDTO = PackageDTO.builder()
                                   .uuid(UUID.randomUUID())
                                   .prefix(PREFIX)
                                   .label("default")
                                   .build();

        updateClassService.addClass(graphIdentifier, packageDTO, PREFIX, "newClass");

        var graph = databasePort.getGraph(graphIdentifier);
        try {
            graph.begin(TxnType.READ);
            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "newClass"), RDF.type.asNode(), RDFS.Class.asNode())).isTrue();
            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "newClass"), RDFS.label.asNode(), new RDFSLabel("newClass", "en").asLangLiteral().asNode())).isTrue();
        } finally {
            graph.end();
        }
    }

    @Test
    void replaceClass_replacesExistingClass() {
        var label = new RDFSLabel("newClass", "en");
        var newClass = ClassUMLAdaptedDTO.builder()
                                         .uuid(UUID.fromString(CLASS_UUID))
                                         .prefix(PREFIX)
                                         .label("newClass")
                                         .build();

        updateClassService.replaceClass(graphIdentifier, newClass);

        var graph = databasePort.getGraph(graphIdentifier);
        try {
            graph.begin(TxnType.READ);

            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "class"), Node.ANY, Node.ANY)).isFalse();
            assertThat(graph.contains(Node.ANY, Node.ANY, NodeFactory.createURI(PREFIX + "class"))).isFalse();

            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "newClass"), RDF.type.asNode(), RDFS.Class.asNode())).isTrue();
            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "newClass"), RDFS.label.asNode(), label.asLangLiteral().asNode())).isTrue();
            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "subClass"), RDFS.subClassOf.asNode(), NodeFactory.createURI(PREFIX + "newClass"))).isTrue();
        } finally {
            graph.end();
        }
    }

    @Test
    void deleteClass_removesClassFromGraph() {
        updateClassService.deleteClass(graphIdentifier, CLASS_UUID);
        var graph = databasePort.getGraph(graphIdentifier);
        try {
            graph.begin(TxnType.READ);
            assertThat(graph.contains(NodeFactory.createURI(PREFIX + "class"), Node.ANY, Node.ANY)).isFalse();
            assertThat(graph.contains(Node.ANY, NodeFactory.createURI(PREFIX + "class"), Node.ANY)).isFalse();
            assertThat(graph.contains(Node.ANY, Node.ANY, NodeFactory.createURI(PREFIX + "class"))).isFalse();
        } finally {
            graph.end();
        }
    }
}
