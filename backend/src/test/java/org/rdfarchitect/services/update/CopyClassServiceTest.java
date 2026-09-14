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

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.PasteClassesRequestDTO;
import org.rdfarchitect.api.dto.PasteSourceClassDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ExpandURIService;
import org.rdfarchitect.services.update.classes.CopyClassReferenceResolver;
import org.rdfarchitect.services.update.classes.CopyClassService;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@SpringBootTest
class CopyClassServiceTest {

    private CopyClassService copyClassService;
    private DatabasePort databasePort;
    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");
    private final GraphIdentifier referenceSourceGraphIdentifier =
            new GraphIdentifier("default", "http://example.org/source");
    private final GraphIdentifier targetGraphIdentifier =
            new GraphIdentifier("default", "http://example.org/target");

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final UUID TARGET_PACKAGE_UUID =
            UUID.fromString("75844dc0-d937-4184-bf6b-d35d8ca6d92a");
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";
    private static final String UNKNOWN_CLASS_UUID = "0f0e0d0c-0b0a-4988-8766-554433221100";
    private static final String DATA_TYPE_UUID = "2c9e0d11-5b47-4ad2-8f3a-6e1c9b2d7f45";
    private static final String OTHER_CLASS_UUID = "1a9b8c77-2d3e-4f50-8a61-7b2c3d4e5f92";
    private static final String ASSOCIATED_CLASS_UUID = "4e6d5c33-8b69-4d74-a1f2-8a3e9b4d5c67";
    private static final String NUMERIC_CLASS_UUID = "5a1c2d3e-4f50-4a61-9b72-8c3d4e5f6a01";
    private static final String DERIVED_CLASS_UUID = "3c7d0e55-4f5a-4b72-8c83-9d4e5f6a7b14";
    private static final String DATA_TYPE_URI = PREFIX + "MyDataType";
    private static final String OTHER_DATA_TYPE_URI = PREFIX + "OtherDataType";
    private static final String ASSOCIATION_TARGET_URI = PREFIX + "associatedClass";
    private static final String SUPER_CLASS_URI = PREFIX + "BaseClass";
    private static final String ROOT_CLASS_URI = PREFIX + "RootClass";
    private static final String VALUE_TYPE_URI = PREFIX + "ValueType";
    private static final String DEEP_TYPE_URI = PREFIX + "DeepType";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        copyClassService =
                new CopyClassService(
                        databasePort,
                        new CopyClassSourceReader(databasePort, new ExpandURIService(databasePort)),
                        new CopyClassReferenceResolver(databasePort),
                        false);
        var file = readMultipartFileFromFile(PATH, "class.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    private void setUpReferenceGraphs() {
        var file = readMultipartFileFromFile(PATH, "class-with-references.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(referenceSourceGraphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(referenceSourceGraphIdentifier, graphSource.graph());

        var targetGraph = GraphFactory.createDefaultGraph();
        var packageNode = NodeFactory.createURI(PREFIX + "newPackage");
        targetGraph.add(packageNode, RDF.type.asNode(), CIMS.classCategory.asNode());
        targetGraph.add(
                packageNode,
                RDFS.label.asNode(),
                new RDFSLabel("newPackage", "en").asLangLiteral().asNode());
        targetGraph.add(
                packageNode,
                RDFA.uuid.asNode(),
                NodeFactory.createLiteralString(TARGET_PACKAGE_UUID.toString()));
        databasePort.createGraph(targetGraphIdentifier, targetGraph);
    }

    private PasteClassesRequestDTO referenceRequest(String... referencesToCopy) {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(true);
        request.setCopyAssociations(true);
        request.setCopyInheritance(true);
        request.setReferencesToCopy(Stream.of(referencesToCopy).map(URI::new).toList());

        request.setSources(List.of(referenceSource(CLASS_UUID)));
        return request;
    }

    private PasteSourceClassDTO referenceSource(String classUUID) {
        var source = new PasteSourceClassDTO();
        source.setSourceDatasetName(referenceSourceGraphIdentifier.datasetName());
        source.setSourceGraphURI(referenceSourceGraphIdentifier.graphUri());
        source.setClassUUID(classUUID);
        return source;
    }

    private PasteSourceClassDTO defaultGraphSource(String classUUID) {
        var source = new PasteSourceClassDTO();
        source.setSourceDatasetName(graphIdentifier.datasetName());
        source.setSourceGraphURI(graphIdentifier.graphUri());
        source.setClassUUID(classUUID);
        return source;
    }

    private CopyClassResponseDTO copyClass(PasteClassesRequestDTO pasteRequest) {
        pasteRequest.setSources(List.of(defaultGraphSource(CLASS_UUID)));

        return copyClassService.copyClasses(pasteRequest, graphIdentifier).get(0);
    }

    @Test
    void copyClass_copyExistingClass() {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(true);
        request.setCopyAssociations(false);

        copyClass(request);

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
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(true);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClass(request);

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
    void copyClass_copyAsAbstract_keepsSuperClassAndDropsConcreteStereotype() {
        setUpReferenceGraphs();

        var request = referenceRequest();
        request.setCopyAsAbstract(true);

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var copyUri = NodeFactory.createURI(PREFIX + "oldLabel");
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            copyUri,
                                            RDFS.subClassOf.asNode(),
                                            NodeFactory.createURI(PREFIX + "BaseClass")))
                    .isTrue();
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            copyUri,
                                            CIMS.stereotype.asNode(),
                                            CIMStereotypes.concrete.asNode()))
                    .isFalse();
        }
    }

    @Test
    void copyClass_selectedInheritanceChain_copiesEveryAncestorAsStub() {
        setUpReferenceGraphs();

        var request = referenceRequest(SUPER_CLASS_URI, ROOT_CLASS_URI);

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, SUPER_CLASS_URI)).isTrue();
            assertThat(containsClass(graph, ROOT_CLASS_URI)).isTrue();
            assertThat(superClassOf(graph, PREFIX + "oldLabel"))
                    .isEqualTo(NodeFactory.createURI(SUPER_CLASS_URI));
            assertThat(superClassOf(graph, SUPER_CLASS_URI))
                    .isEqualTo(NodeFactory.createURI(ROOT_CLASS_URI));
        }
    }

    @Test
    void copyClass_ancestorLeftBehind_keepsItAsAReferenceOfTheCopiedStub() {
        setUpReferenceGraphs();

        var request = referenceRequest(SUPER_CLASS_URI);

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, ROOT_CLASS_URI)).isFalse();
            assertThat(superClassOf(graph, SUPER_CLASS_URI))
                    .isEqualTo(NodeFactory.createURI(ROOT_CLASS_URI));
        }
    }

    @Test
    void copyClass_withoutInheritance_dropsSuperClassAndDoesNotCopyIt() {
        setUpReferenceGraphs();

        var request = referenceRequest(SUPER_CLASS_URI);
        request.setCopyInheritance(false);

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var copyUri = NodeFactory.createURI(PREFIX + "oldLabel");
            assertThat(ctx.getRdfGraph().contains(copyUri, RDFS.subClassOf.asNode(), Node.ANY))
                    .isFalse();
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "BaseClass")).isFalse();
        }
    }

    @Test
    void copyClass_selectedSuperClass_isCopiedAsStub() {
        setUpReferenceGraphs();

        var request = referenceRequest(SUPER_CLASS_URI);

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "BaseClass")).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "oldLabel"),
                                    RDFS.subClassOf.asNode(),
                                    NodeFactory.createURI(PREFIX + "BaseClass")))
                    .isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "BaseClass.attribute"),
                                    Node.ANY,
                                    Node.ANY))
                    .isFalse();
        }
    }

    @Test
    void copyClass_returnsNewClassUUID() {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        var response = copyClass(request);

        assertThat(response).isNotNull();
        assertThat(response.getUuid()).isNotEqualTo(CLASS_UUID);
    }

    @Test
    void copyClasses_multipleSourcesWithDuplicate_singleCallGetsUniqueLabels() {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(true);
        request.setCopyAssociations(false);

        var source = new PasteSourceClassDTO();
        source.setSourceDatasetName(graphIdentifier.datasetName());
        source.setSourceGraphURI(graphIdentifier.graphUri());
        source.setClassUUID(CLASS_UUID);
        var duplicateSource = new PasteSourceClassDTO();
        duplicateSource.setSourceDatasetName(graphIdentifier.datasetName());
        duplicateSource.setSourceGraphURI(graphIdentifier.graphUri());
        duplicateSource.setClassUUID(CLASS_UUID);
        request.setSources(List.of(source, duplicateSource));

        var responses = copyClassService.copyClasses(request, graphIdentifier);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("oldLabel-Copy");
        assertThat(responses.get(1).getName()).isEqualTo("oldLabel-Copy(1)");
        assertThat(responses.get(0).getUuid()).isNotEqualTo(responses.get(1).getUuid());

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
    void copyClass_copyTwice_secondCopyGetsCounter() {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClass(request);
        copyClass(request);

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
    void copyClass_withoutReferenceOptions_leavesDataTypeAndAssociationTargetBehind() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(referenceRequest(), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "MyDataType")).isFalse();
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "associatedClass")).isFalse();
        }
    }

    @Test
    void copyClass_selectedDataType_isCopiedWithItsAttributes() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(referenceRequest(DATA_TYPE_URI), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "MyDataType")).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "MyDataType"),
                                    RDFS.label.asNode(),
                                    new RDFSLabel("MyDataType", "en").asLangLiteral().asNode()))
                    .isTrue();
            assertThat(
                            graph.contains(
                                    Node.ANY,
                                    RDFS.domain.asNode(),
                                    NodeFactory.createURI(PREFIX + "MyDataType")))
                    .isTrue();
            assertThat(containsClass(graph, PREFIX + "associatedClass")).isFalse();
        }
    }

    @Test
    void copyClass_selectedAssociationTarget_isCopiedWithoutItsMembers() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(
                referenceRequest(ASSOCIATION_TARGET_URI), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "associatedClass")).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "associatedClass"),
                                    CIMS.belongsToCategory.asNode(),
                                    NodeFactory.createURI(PREFIX + "newPackage")))
                    .isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "associatedClass.attribute"),
                                    Node.ANY,
                                    Node.ANY))
                    .isFalse();
            assertThat(containsClass(graph, PREFIX + "MyDataType")).isFalse();
        }
    }

    @Test
    void copyClasses_dataTypeIsAlsoASelectedSource_isPastedOnlyOnce() {
        setUpReferenceGraphs();

        var request = referenceRequest(DATA_TYPE_URI, ASSOCIATION_TARGET_URI);
        request.setSources(List.of(referenceSource(CLASS_UUID), referenceSource(DATA_TYPE_UUID)));

        var responses = copyClassService.copyClasses(request, targetGraphIdentifier);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(1).getName()).isEqualTo("MyDataType");

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "MyDataType-Copy")).isFalse();
            assertThat(
                            graph.find(
                                            NodeFactory.createURI(PREFIX + "MyDataType"),
                                            RDFS.label.asNode(),
                                            Node.ANY)
                                    .toList())
                    .hasSize(1);
            assertThat(
                            graph.find(
                                            NodeFactory.createURI(PREFIX + "MyDataType.value"),
                                            RDFS.domain.asNode(),
                                            Node.ANY)
                                    .toList())
                    .hasSize(1);
        }
    }

    @Test
    void copyClasses_classReferencedAsTargetAndAsDataType_isCopiedWithItsMembers() {
        setUpReferenceGraphs();

        // The first source only needs ex:associatedClass as an association target, which alone
        // would copy it as a stub. The second one uses it as an attribute data type, which needs
        // the whole class.
        var request = referenceRequest(DATA_TYPE_URI, ASSOCIATION_TARGET_URI);
        request.setSources(List.of(referenceSource(CLASS_UUID), referenceSource(OTHER_CLASS_UUID)));

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "associatedClass")).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "associatedClass.attribute"),
                                    RDFS.domain.asNode(),
                                    NodeFactory.createURI(PREFIX + "associatedClass")))
                    .isTrue();
        }
    }

    @Test
    void copyClass_superClassIsAlsoAnAssociationTarget_isCopiedOnce() {
        setUpReferenceGraphs();

        var request = referenceRequest(ASSOCIATION_TARGET_URI);
        request.setSources(List.of(referenceSource(DERIVED_CLASS_UUID)));

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(
                            graph.find(
                                            NodeFactory.createURI(PREFIX + "associatedClass"),
                                            RDFS.label.asNode(),
                                            Node.ANY)
                                    .toList())
                    .hasSize(1);
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "derivedClass"),
                                    RDFS.subClassOf.asNode(),
                                    NodeFactory.createURI(PREFIX + "associatedClass")))
                    .isTrue();
        }
    }

    @Test
    void copyClass_oneOfTwoDataTypesSelected_copiesOnlyTheSelectedOne() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(referenceRequest(DATA_TYPE_URI), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), DATA_TYPE_URI)).isTrue();
            assertThat(containsClass(ctx.getRdfGraph(), OTHER_DATA_TYPE_URI)).isFalse();
        }
    }

    @Test
    void copyClass_dataTypeWithOwnDataType_copiesItAlongWithoutBeingSelected() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(referenceRequest(DATA_TYPE_URI), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, DATA_TYPE_URI)).isTrue();
            assertThat(containsClass(graph, VALUE_TYPE_URI)).isTrue();
            assertThat(containsClass(graph, DEEP_TYPE_URI)).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "MyDataType.value"),
                                    CIMS.datatype.asNode(),
                                    NodeFactory.createURI(VALUE_TYPE_URI)))
                    .isTrue();
        }
    }

    @Test
    void copyClass_dataTypeNotSelected_doesNotCopyItsOwnDataType() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(referenceRequest(), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), VALUE_TYPE_URI)).isFalse();
        }
    }

    @Test
    void copyClass_referencedClassAlreadyInTargetGraph_isNotCopiedAgain() {
        setUpReferenceGraphs();

        copyClassService.copyClasses(
                referenceRequest(DATA_TYPE_URI, ASSOCIATION_TARGET_URI), targetGraphIdentifier);
        copyClassService.copyClasses(
                referenceRequest(DATA_TYPE_URI, ASSOCIATION_TARGET_URI), targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "MyDataType-Copy")).isFalse();
            assertThat(containsClass(graph, PREFIX + "associatedClass-Copy")).isFalse();
            assertThat(
                            graph.find(
                                            NodeFactory.createURI(PREFIX + "MyDataType"),
                                            RDFS.label.asNode(),
                                            Node.ANY)
                                    .toList())
                    .hasSize(1);
        }
    }

    @Test
    void copyClass_copyThreeTimes_thirdCopyGetsIncrementedCounter() {
        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAsAbstract(false);
        request.setCopyAttributes(false);
        request.setCopyAssociations(false);

        copyClass(request);
        copyClass(request);
        copyClass(request);

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

    @Test
    void copyClasses_oneSourceClassIsNotInTheGraph_pastesNothing() {
        setUpReferenceGraphs();

        var request = referenceRequest();
        request.setSources(
                List.of(referenceSource(UNKNOWN_CLASS_UUID), referenceSource(CLASS_UUID)));

        var responses = copyClassService.copyClasses(request, targetGraphIdentifier);

        assertThat(responses).isEmpty();
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "oldLabel")).isFalse();
        }
    }

    @Test
    void copyClasses_oneSourceClassIsOnlyLeftAsAReferencedUuid_pastesNothing() {
        setUpReferenceGraphs();
        stripToUuid(PREFIX + "associatedClass");

        var request = referenceRequest();
        request.setSources(
                List.of(referenceSource(CLASS_UUID), referenceSource(ASSOCIATED_CLASS_UUID)));

        var responses = copyClassService.copyClasses(request, targetGraphIdentifier);

        assertThat(responses).isEmpty();
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "oldLabel")).isFalse();
        }
    }

    @Test
    void copyClasses_noSourceClassIsInTheGraph_pastesNothing() {
        setUpReferenceGraphs();

        var request = referenceRequest();
        request.setSources(List.of(referenceSource(UNKNOWN_CLASS_UUID)));

        var responses = copyClassService.copyClasses(request, targetGraphIdentifier);

        assertThat(responses).isEmpty();
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "oldLabel")).isFalse();
        }
    }

    @Test
    void copyClass_inverseAssociationIsIncomplete_pastesClassWithoutThatAssociation() {
        setUpReferenceGraphs();
        removeFromSourceGraph(
                PREFIX + "associatedClass.class",
                RDFS.domain.asNode(),
                NodeFactory.createURI(PREFIX + "associatedClass"));

        var responses = copyClassService.copyClasses(referenceRequest(), targetGraphIdentifier);

        assertThat(responses).extracting(CopyClassResponseDTO::getName).containsExactly("oldLabel");
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(containsClass(graph, PREFIX + "oldLabel")).isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "oldLabel.associatedClass"),
                                    Node.ANY,
                                    Node.ANY))
                    .isFalse();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "oldLabel.attribute"),
                                    RDFS.domain.asNode(),
                                    NodeFactory.createURI(PREFIX + "oldLabel")))
                    .isTrue();
        }
    }

    @Test
    void copyClass_targetPackageIsNotInTheTargetGraph_pastesNothing() {
        setUpReferenceGraphs();

        var request = referenceRequest();
        request.setTargetPackageUUID(UUID.fromString(UNKNOWN_CLASS_UUID));

        assertThatThrownBy(() -> copyClassService.copyClasses(request, targetGraphIdentifier))
                .isInstanceOf(DataAccessException.class);
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "oldLabel")).isFalse();
        }
    }

    @Test
    void copyClass_labelIsANumber_pastesACopyInsteadOfMergingIntoTheSourceClass() {
        addClassToGraph(graphIdentifier, PREFIX + "123", "123", NUMERIC_CLASS_UUID);

        var request = new PasteClassesRequestDTO();
        request.setTargetPackageUUID(TARGET_PACKAGE_UUID);
        request.setCopyAttributes(true);
        request.setSources(List.of(defaultGraphSource(NUMERIC_CLASS_UUID)));

        var responses = copyClassService.copyClasses(request, graphIdentifier);

        assertThat(responses).extracting(CopyClassResponseDTO::getName).containsExactly("123-Copy");
        assertThat(responses.get(0).getUuid()).isNotEqualTo(NUMERIC_CLASS_UUID);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            assertThat(containsClass(ctx.getRdfGraph(), PREFIX + "123-Copy")).isTrue();
        }
    }

    @Test
    void copyClasses_associationTargetIsPastedWithANewLabel_pointsAtTheNewClass() {
        setUpReferenceGraphs();
        addClassToGraph(targetGraphIdentifier, PREFIX + "associatedClass", "associatedClass", null);

        var request = referenceRequest();
        request.setSources(
                List.of(referenceSource(CLASS_UUID), referenceSource(ASSOCIATED_CLASS_UUID)));

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "oldLabel.associatedClass"),
                                    RDFS.range.asNode(),
                                    NodeFactory.createURI(PREFIX + "associatedClass-Copy")))
                    .isTrue();
            assertThat(
                            graph.contains(
                                    NodeFactory.createURI(PREFIX + "associatedClass-Copy.class"),
                                    RDFS.domain.asNode(),
                                    NodeFactory.createURI(PREFIX + "associatedClass-Copy")))
                    .isTrue();
        }
    }

    @Test
    void copyClasses_superClassIsPastedWithANewLabel_pointsAtTheNewClass() {
        setUpReferenceGraphs();
        addClassToGraph(targetGraphIdentifier, PREFIX + "associatedClass", "associatedClass", null);

        var request = referenceRequest();
        request.setSources(
                List.of(
                        referenceSource(DERIVED_CLASS_UUID),
                        referenceSource(ASSOCIATED_CLASS_UUID)));

        copyClassService.copyClasses(request, targetGraphIdentifier);

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI(PREFIX + "derivedClass"),
                                            RDFS.subClassOf.asNode(),
                                            NodeFactory.createURI(PREFIX + "associatedClass-Copy")))
                    .isTrue();
        }
    }

    private void addClassToGraph(
            GraphIdentifier graph, String classUri, String label, String classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graph).begin(ReadWrite.WRITE)) {
            var classNode = NodeFactory.createURI(classUri);
            ctx.getRdfGraph().add(classNode, RDF.type.asNode(), RDFS.Class.asNode());
            ctx.getRdfGraph()
                    .add(
                            classNode,
                            RDFS.label.asNode(),
                            new RDFSLabel(label, "en").asLangLiteral().asNode());
            if (classUUID != null) {
                ctx.getRdfGraph()
                        .add(
                                classNode,
                                RDFA.uuid.asNode(),
                                NodeFactory.createLiteralString(classUUID));
            }
            ctx.commit("Added a class for the test.");
        }
    }

    private void stripToUuid(String subjectUri) {
        try (var ctx =
                databasePort
                        .getGraphWithContext(referenceSourceGraphIdentifier)
                        .begin(ReadWrite.WRITE)) {
            var subject = NodeFactory.createURI(subjectUri);
            ctx.getRdfGraph()
                    .find(subject, Node.ANY, Node.ANY)
                    .toList()
                    .forEach(
                            triple -> {
                                if (!triple.getPredicate().equals(RDFA.uuid.asNode())) {
                                    ctx.getRdfGraph().delete(triple);
                                }
                            });
            ctx.commit("Left a referenced uuid behind for the test.");
        }
    }

    private void removeFromSourceGraph(String subjectUri, Node predicate, Node object) {
        try (var ctx =
                databasePort
                        .getGraphWithContext(referenceSourceGraphIdentifier)
                        .begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().remove(NodeFactory.createURI(subjectUri), predicate, object);
            ctx.commit("Removed a triple for the test.");
        }
    }

    private Node superClassOf(Graph graph, String classUri) {
        var superClasses =
                graph.find(NodeFactory.createURI(classUri), RDFS.subClassOf.asNode(), Node.ANY)
                        .toList();
        return superClasses.isEmpty() ? null : superClasses.getFirst().getObject();
    }

    private boolean containsClass(Graph graph, String classUri) {
        return graph.contains(
                NodeFactory.createURI(classUri), RDF.type.asNode(), RDFS.Class.asNode());
    }
}
