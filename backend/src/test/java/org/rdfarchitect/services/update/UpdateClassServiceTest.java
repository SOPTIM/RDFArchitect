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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.diagrams.CustomDiagramService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.update.classes.UpdateClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class UpdateClassServiceTest {

    private UpdateClassService updateClassService;
    private DatabasePort databasePort;
    private UpdateClassLayoutService mockUpdateClassLayoutService;
    private CustomDiagramService mockCustomDiagramService;
    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    @Autowired private ClassUMLAdaptedMapper classMapper;
    @Autowired private PackageMapper packageMapper;

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        mockUpdateClassLayoutService = mock(UpdateClassLayoutService.class);
        mockCustomDiagramService = mock(CustomDiagramService.class);
        updateClassService =
                new UpdateClassService(
                        databasePort,
                        classMapper,
                        packageMapper,
                        mockUpdateClassLayoutService,
                        mockUpdateClassLayoutService,
                        mockUpdateClassLayoutService,
                        false,
                        mockUpdateClassLayoutService,
                        mockCustomDiagramService);
        var file = readMultipartFileFromFile(PATH, "class.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    @Test
    void addClass_createsNewClass() {
        var packageDTO =
                PackageDTO.builder()
                        .uuid(UUID.randomUUID())
                        .prefix(PREFIX)
                        .label("default")
                        .build();

        updateClassService.addClass(graphIdentifier, packageDTO, PREFIX, "newClass", null);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "newClass"),
                                            RDF.type.asNode(),
                                            RDFS.Class.asNode()))
                    .isTrue();
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "newClass"),
                                            RDFS.label.asNode(),
                                            new RDFSLabel("newClass", "en")
                                                    .asLangLiteral()
                                                    .asNode()))
                    .isTrue();
        }
    }

    @Test
    void addClass_uriIsReferencedOnly_keepsUuidOfReferencedResource() {
        var referencedUuid = addReferencedOnlyResource("ghost");

        var packageDTO =
                PackageDTO.builder()
                        .uuid(UUID.randomUUID())
                        .prefix(PREFIX)
                        .label("default")
                        .build();

        var newUuid =
                updateClassService.addClass(graphIdentifier, packageDTO, PREFIX, "ghost", null);

        assertThat(newUuid).isEqualTo(referencedUuid);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            assertThat(
                            model.listStatements(
                                            model.getResource(PREFIX + "ghost"),
                                            RDFA.uuid,
                                            (RDFNode) null)
                                    .toList())
                    .hasSize(1);
        }
    }

    @Test
    void addClass_packageWithSameIriExists_throwsConflict() {
        var packageUri = PREFIX + "packageCollision";
        var graphCtx = databasePort.getGraphWithContext(graphIdentifier);
        try (var ctx = graphCtx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph()
                    .add(
                            NodeFactory.createURI(packageUri),
                            RDF.type.asNode(),
                            CIMS.classCategory.asNode());
            ctx.commit();
        }

        var packageDTO =
                PackageDTO.builder()
                        .uuid(UUID.randomUUID())
                        .prefix(PREFIX)
                        .label("default")
                        .build();

        assertThatThrownBy(
                        () ->
                                updateClassService.addClass(
                                        graphIdentifier,
                                        packageDTO,
                                        PREFIX,
                                        "packageCollision",
                                        null))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("package with the same IRI");

        try (var ctx = graphCtx.begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(packageUri),
                                            RDF.type.asNode(),
                                            RDFS.Class.asNode()))
                    .isFalse();
        }
    }

    @Test
    void replaceClass_replacesExistingClass() {
        var label = new RDFSLabel("newClass", "en");
        var newClass =
                ClassUMLAdaptedDTO.builder()
                        .uuid(UUID.fromString(CLASS_UUID))
                        .prefix(PREFIX)
                        .label("newClass")
                        .build();

        updateClassService.replaceClass(graphIdentifier, newClass);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "class"),
                                            Node.ANY,
                                            Node.ANY))
                    .isFalse();
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            Node.ANY,
                                            Node.ANY,
                                            NodeFactory.createURI(PREFIX + "class")))
                    .isFalse();

            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "newClass"),
                                            RDF.type.asNode(),
                                            RDFS.Class.asNode()))
                    .isTrue();
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "newClass"),
                                            RDFS.label.asNode(),
                                            label.asLangLiteral().asNode()))
                    .isTrue();
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "subClass"),
                                            RDFS.subClassOf.asNode(),
                                            NodeFactory.createURI(PREFIX + "newClass")))
                    .isTrue();
        }
    }

    @Test
    void replaceClass_takesOverReferencedOnlyUri_cleansUpItsLayoutData() {
        var referencedUuid = addReferencedOnlyResource("ghost");
        var newClass =
                ClassUMLAdaptedDTO.builder()
                        .uuid(UUID.fromString(CLASS_UUID))
                        .prefix(PREFIX)
                        .label("ghost")
                        .build();

        updateClassService.replaceClass(graphIdentifier, newClass);

        // The referenced only resource is gone, so anything keyed on its uuid has to go with it
        verify(mockUpdateClassLayoutService).deleteClassLayoutData(graphIdentifier, referencedUuid);
        verify(mockCustomDiagramService).removeFromAllDiagrams(graphIdentifier, referencedUuid);
    }

    @Test
    void replaceClass_uriIsFree_keepsLayoutDataUntouched() {
        var newClass =
                ClassUMLAdaptedDTO.builder()
                        .uuid(UUID.fromString(CLASS_UUID))
                        .prefix(PREFIX)
                        .label("newClass")
                        .build();

        updateClassService.replaceClass(graphIdentifier, newClass);

        verify(mockUpdateClassLayoutService, never())
                .deleteClassLayoutData(any(GraphIdentifier.class), any(UUID.class));
        verify(mockCustomDiagramService, never())
                .removeFromAllDiagrams(any(GraphIdentifier.class), any(UUID.class));
    }

    @Test
    void addClass_namesTheClassInTheChangeLogWithoutItsUuid() {
        var packageDTO =
                PackageDTO.builder()
                        .uuid(UUID.randomUUID())
                        .prefix(PREFIX)
                        .label("default")
                        .build();

        updateClassService.addClass(graphIdentifier, packageDTO, PREFIX, "newClass", null);

        assertThat(latestChangeMessage()).isEqualTo("Added class \"newClass\"");
    }

    @Test
    void replaceClass_namesTheClassInTheChangeLogWithoutItsUuid() {
        var newClass =
                ClassUMLAdaptedDTO.builder()
                        .uuid(UUID.fromString(CLASS_UUID))
                        .prefix(PREFIX)
                        .label("newClass")
                        .build();

        updateClassService.replaceClass(graphIdentifier, newClass);

        assertThat(latestChangeMessage()).isEqualTo("Updated class \"newClass\"");
    }

    @Test
    void deleteClass_namesTheClassInTheChangeLogWithoutItsUuid() {
        updateClassService.deleteClass(graphIdentifier, UUID.fromString(CLASS_UUID));

        assertThat(latestChangeMessage()).isEqualTo("Deleted class \"oldLabel\"");
    }

    private String latestChangeMessage() {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ctx.getChangeLog().getUndoHistory().getFirst().getMessage();
        }
    }

    /** Referencing a uri that nothing defines makes it a referenced only resource with a uuid. */
    private UUID addReferencedOnlyResource(String label) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph()
                    .add(
                            NodeFactory.createURI(PREFIX + "class.associatedClass"),
                            RDFS.range.asNode(),
                            NodeFactory.createURI(PREFIX + label));
            ctx.commit("referenced only resource");
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            return UUID.fromString(
                    model.getResource(PREFIX + label).getProperty(RDFA.uuid).getString());
        }
    }

    @Test
    void deleteClass_removesClassResourceFromGraph() {
        updateClassService.deleteClass(graphIdentifier, UUID.fromString(CLASS_UUID));
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var classResource = model.createResource(PREFIX + "class");
            var statements = model.listStatements(classResource, null, (RDFNode) null).toList();
            assertThat(statements).hasSize(1);
            assertThat(statements.getFirst().getSubject()).hasToString(PREFIX + "class");
            assertThat(statements.getFirst().getPredicate()).hasToString(RDFA.uuid.getURI());
            assertThat(statements.getFirst().getObject())
                    .hasToString("43836908-c7f7-4749-bb8b-3ac9250de655");
            assertThat(
                            model.listStatements(
                                            null,
                                            model.createProperty(PREFIX + "class"),
                                            (RDFNode) null)
                                    .hasNext())
                    .isFalse();
        }
    }
}
