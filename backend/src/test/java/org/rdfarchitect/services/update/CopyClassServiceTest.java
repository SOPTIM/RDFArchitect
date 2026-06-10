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

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.CopyClassRequestDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.update.classes.CopyClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class CopyClassServiceTest {

    private CopyClassService copyClassService;
    private DatabasePort databasePort;
    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    @Autowired private PackageMapper packageMapper;

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        copyClassService = new CopyClassService(databasePort, packageMapper, false);
        var file = readMultipartFileFromFile(PATH, "class.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    @Test
    void copyClass_copyExistingClass() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(true);
        request.setCopyAssociations(false);

        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy"),
                                            RDF.type.asNode(),
                                            RDFS.Class.asNode()))
                    .isTrue();

            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy"),
                                            RDFS.label.asNode(),
                                            new RDFSLabel("oldLabel-Copy", "en")
                                                    .asLangLiteral()
                                                    .asNode()))
                    .isTrue();
        }
    }

    @Test
    void copyClass_copyExistingClass_abstract() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(true);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy"),
                                            RDF.type.asNode(),
                                            RDFS.Class.asNode()))
                    .isTrue();

            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            Node.ANY,
                                            RDFS.domain.asNode(),
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy")))
                    .isFalse();
        }
    }

    @Test
    void copyClass_copyAsAbstract_doesNotCopySuperClass() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(true);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var copyUri = NodeFactory.createURI(PREFIX + "oldLabel-Copy");
            assertThat(ctx.getRdfGraph().contains(copyUri, RDFS.subClassOf.asNode(), Node.ANY))
                    .isFalse();
        }
    }

    @Test
    void copyClass_returnsNewClassUUID() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        var response =
                copyClassService.copyClass(
                        graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        assertThat(response).isNotNull();
        assertThat(response.getUuid()).isNotEqualTo(CLASS_UUID);
    }

    @Test
    void copyClass_copyTwice_secondCopyGetsCounter() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);
        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy"),
                                            RDFS.label.asNode(),
                                            new RDFSLabel("oldLabel-Copy", "en")
                                                    .asLangLiteral()
                                                    .asNode()))
                    .isTrue();

            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy(1)"),
                                            RDFS.label.asNode(),
                                            new RDFSLabel("oldLabel-Copy(1)", "en")
                                                    .asLangLiteral()
                                                    .asNode()))
                    .isTrue();
        }
    }

    @Test
    void copyClass_copyThreeTimes_thirdCopyGetsIncrementedCounter() {
        var targetPackageDTO =
                PackageDTO.builder()
                        .uuid(UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a"))
                        .prefix(PREFIX)
                        .label("newPackage")
                        .build();

        var request = new CopyClassRequestDTO();
        request.setTargetPackage(targetPackageDTO);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);
        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);
        copyClassService.copyClass(
                graphIdentifier, UUID.fromString(CLASS_UUID), graphIdentifier, request);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "oldLabel-Copy(2)"),
                                            RDFS.label.asNode(),
                                            new RDFSLabel("oldLabel-Copy(2)", "en")
                                                    .asLangLiteral()
                                                    .asNode()))
                    .isTrue();
        }
    }
}
