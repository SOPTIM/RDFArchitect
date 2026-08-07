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
import static org.assertj.core.api.Assertions.tuple;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.PastePreviewRequestDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO.PasteReferenceDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO.PasteUsageDTO;
import org.rdfarchitect.api.dto.PasteSourceClassDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ExpandURIService;
import org.rdfarchitect.services.update.classes.CopyClassReference.Kind;
import org.rdfarchitect.services.update.classes.CopyClassReferenceResolver;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader;
import org.rdfarchitect.services.update.classes.PastePreviewService;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@SpringBootTest
class PastePreviewServiceTest {

    private PastePreviewService pastePreviewService;
    private DatabasePort databasePort;

    private final GraphIdentifier sourceGraphIdentifier =
            new GraphIdentifier("default", "http://example.org/source");
    private final GraphIdentifier targetGraphIdentifier =
            new GraphIdentifier("default", "http://example.org/target");

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";
    private static final String OTHER_CLASS_UUID = "1a9b8c77-2d3e-4f50-8a61-7b2c3d4e5f92";
    private static final String DERIVED_CLASS_UUID = "3c7d0e55-4f5a-4b72-8c83-9d4e5f6a7b14";
    private static final String ASSOCIATED_CLASS_UUID = "4e6d5c33-8b69-4d74-a1f2-8a3e9b4d5c67";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        pastePreviewService =
                new PastePreviewService(
                        databasePort,
                        new CopyClassSourceReader(databasePort, new ExpandURIService(databasePort)),
                        new CopyClassReferenceResolver(databasePort));

        var file = readMultipartFileFromFile(PATH, "class-with-references.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(sourceGraphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(sourceGraphIdentifier, graphSource.graph());
        databasePort.createGraph(targetGraphIdentifier, GraphFactory.createDefaultGraph());
    }

    private PasteSourceClassDTO source(String classUUID) {
        var source = new PasteSourceClassDTO();
        source.setSourceDatasetName(sourceGraphIdentifier.datasetName());
        source.setSourceGraphURI(sourceGraphIdentifier.graphUri());
        source.setClassUUID(classUUID);
        return source;
    }

    private PastePreviewRequestDTO previewRequest(String... classUUIDs) {
        var request = new PastePreviewRequestDTO();
        request.setSources(Stream.of(classUUIDs).map(this::source).toList());
        return request;
    }

    private List<PasteReferenceDTO> missing(PastePreviewResponseDTO preview, Kind kind) {
        return preview.missing().getOrDefault(kind.name(), List.of());
    }

    @Test
    void previewPaste_emptyTargetGraph_namesEveryMissingClass() {
        var preview =
                pastePreviewService.previewPaste(previewRequest(CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("MyDataType", "OtherDataType", "ValueType");
        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("associatedClass");
        assertThat(missing(preview, Kind.SUPER_CLASS))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("BaseClass");
        assertThat(missing(preview, Kind.DATA_TYPE).get(0).uri())
                .isEqualTo(new URI("http://example.org#MyDataType"));
    }

    @Test
    void previewPaste_namesTheMembersThatUseEachMissingClass() {
        var preview =
                pastePreviewService.previewPaste(previewRequest(CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE))
                .extracting(PasteReferenceDTO::label, PasteReferenceDTO::usedBy)
                .containsExactly(
                        tuple("MyDataType", List.of(new PasteUsageDTO("oldLabel", "attribute"))),
                        tuple(
                                "OtherDataType",
                                List.of(new PasteUsageDTO("oldLabel", "otherAttribute"))),
                        tuple(
                                "ValueType",
                                List.of(new PasteUsageDTO("oldLabel", "valueAttribute"))));
        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::usedBy)
                .containsExactly(List.of(new PasteUsageDTO("oldLabel", "associatedClass")));
        assertThat(missing(preview, Kind.SUPER_CLASS))
                .extracting(PasteReferenceDTO::usedBy)
                .containsExactly(List.of(new PasteUsageDTO("oldLabel", null)));
    }

    @Test
    void previewPaste_dataTypeUsesAnotherListedDataType_namesItAsRequired() {
        var preview =
                pastePreviewService.previewPaste(previewRequest(CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE))
                .extracting(PasteReferenceDTO::label, PasteReferenceDTO::requires)
                .containsExactly(
                        tuple("MyDataType", List.of(new URI("http://example.org#ValueType"))),
                        tuple("OtherDataType", List.of()),
                        tuple("ValueType", List.of()));
        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::requires)
                .containsExactly(List.of());
    }

    @Test
    void previewPaste_missingClassUsedAsBothKinds_namesTheMemberPerKind() {
        var preview =
                pastePreviewService.previewPaste(
                        previewRequest(DERIVED_CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::usedBy)
                .containsExactly(List.of(new PasteUsageDTO("derivedClass", "associatedClass")));
        assertThat(missing(preview, Kind.SUPER_CLASS))
                .extracting(PasteReferenceDTO::usedBy)
                .containsExactly(List.of(new PasteUsageDTO("derivedClass", null)));
    }

    @Test
    void previewPaste_classWithoutAssociationsAndSuperClass_namesNoneOfThem() {
        var preview =
                pastePreviewService.previewPaste(
                        previewRequest(OTHER_CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.ASSOCIATION_TARGET)).isEmpty();
        assertThat(missing(preview, Kind.SUPER_CLASS)).isEmpty();
    }

    @Test
    void previewPaste_superClassIsAlsoAnAssociationTarget_namesItAsBoth() {
        var preview =
                pastePreviewService.previewPaste(
                        previewRequest(DERIVED_CLASS_UUID), targetGraphIdentifier);

        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("associatedClass");
        assertThat(missing(preview, Kind.SUPER_CLASS))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("associatedClass");
    }

    @Test
    void previewPaste_referencedClassIsPastedAlong_doesNotNameItAsMissing() {
        var preview =
                pastePreviewService.previewPaste(
                        previewRequest(OTHER_CLASS_UUID, ASSOCIATED_CLASS_UUID),
                        targetGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("ValueType");
    }

    @Test
    void previewPaste_superClassAndAssociationTargetArePastedAlong_namesNothing() {
        var preview =
                pastePreviewService.previewPaste(
                        previewRequest(DERIVED_CLASS_UUID, ASSOCIATED_CLASS_UUID),
                        targetGraphIdentifier);

        assertThat(missing(preview, Kind.ASSOCIATION_TARGET))
                .extracting(PasteReferenceDTO::label)
                .containsExactly("oldLabel");
        assertThat(missing(preview, Kind.SUPER_CLASS)).isEmpty();
    }

    @Test
    void previewPaste_targetGraphContainsReferences_namesNothing() {
        var preview =
                pastePreviewService.previewPaste(previewRequest(CLASS_UUID), sourceGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE)).isEmpty();
        assertThat(missing(preview, Kind.ASSOCIATION_TARGET)).isEmpty();
        assertThat(missing(preview, Kind.SUPER_CLASS)).isEmpty();
    }

    @Test
    void previewPaste_withoutSources_namesNothing() {
        var preview =
                pastePreviewService.previewPaste(
                        new PastePreviewRequestDTO(), targetGraphIdentifier);

        assertThat(missing(preview, Kind.DATA_TYPE)).isEmpty();
        assertThat(missing(preview, Kind.ASSOCIATION_TARGET)).isEmpty();
        assertThat(missing(preview, Kind.SUPER_CLASS)).isEmpty();
    }
}
