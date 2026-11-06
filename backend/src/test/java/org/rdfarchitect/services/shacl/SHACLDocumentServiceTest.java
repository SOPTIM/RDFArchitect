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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.exception.database.StaleWriteException;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Managing a graph's shapes documents through the service. */
class SHACLDocumentServiceTest {

    private static final GraphIdentifier GRAPH = new GraphIdentifier("cgmes", "http://ex.org/EQ");

    /** Deliberately carries a comment and spacing that a Jena round-trip would destroy. */
    private static final String TURTLE =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # cardinality of a line segment
            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass ex:ACLineSegment .
            """;

    private static final String RDF_XML =
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:sh="http://www.w3.org/ns/shacl#">
              <rdf:Description rdf:about="http://ex.org/EQ#TerminalShape">
                <sh:targetClass rdf:resource="http://ex.org/EQ#Terminal"/>
              </rdf:Description>
            </rdf:RDF>
            """;

    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createWorkspaceIfAbsent(GRAPH.datasetName());
        try (var transaction =
                databasePort.beginTransaction(GRAPH.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH.graphUri(), GraphFactory.createDefaultGraph());
            transaction.commit("Created the graph");
        }
        databasePort.enableEditing(GRAPH.datasetName());
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private ShapesDocumentInfo createTurtleDocument(String name) {
        return service.createShapesDocument(
                GRAPH.datasetName(), List.of(GRAPH.graphUri()), name, name, TURTLE, Lang.TURTLE);
    }

    @Test
    void newGraphHasNoDocuments() {
        assertThat(service.listShapesDocuments(GRAPH.datasetName())).isEmpty();
    }

    @Test
    void createdDocumentIsListedWithItsMetadata() {
        var created = createTurtleDocument("eq.ttl");

        assertThat(created.getOrigin()).isEqualTo(ShapesDocument.Origin.IMPORTED);
        assertThat(created.getTripleCount()).isPositive();
        assertThat(service.listShapesDocuments(GRAPH.datasetName()))
                .extracting(ShapesDocumentInfo::getName)
                .containsExactly("eq.ttl");
    }

    @Test
    void turtleIsKeptExactlyAsGiven() {
        var created = createTurtleDocument("eq.ttl");

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId()))
                .isEqualTo(TURTLE);
    }

    @Test
    void otherSyntaxesAreConvertedToTurtleOnImport() {
        // The editor only works in Turtle, so keeping the original RDF/XML would hand it something
        // it cannot show.
        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "tp.rdf",
                        "tp.rdf",
                        RDF_XML,
                        Lang.RDFXML);

        var text = service.getShapesDocumentText(GRAPH.datasetName(), created.getId());

        assertThat(text).doesNotContain("rdf:RDF").contains("TerminalShape");
    }

    @Test
    void contentWrittenWithoutAnAuthoredTextIsStillReadable() {
        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "eq.ttl",
                        null,
                        TURTLE,
                        Lang.TURTLE);

        service.replaceShapesDocumentText(GRAPH.datasetName(), created.getId(), TURTLE);

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId()))
                .isEqualTo(TURTLE);
    }

    @Test
    void aSaveMadeToTheStoredTextIsWritten() {
        var created = createTurtleDocument("eq.ttl");
        var read = service.getShapesDocumentText(GRAPH.datasetName(), created.getId());

        service.replaceShapesDocumentText(
                GRAPH.datasetName(),
                created.getId(),
                read + "# mine\n",
                SHACLDocumentUseCase.revisionOf(read));

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId()))
                .endsWith("# mine\n");
    }

    @Test
    void aSaveMadeToTextThatHasSinceChangedIsRefused() {
        var created = createTurtleDocument("eq.ttl");
        var read = service.getShapesDocumentText(GRAPH.datasetName(), created.getId());
        // Someone else saves in the meantime — another tab, or an undo.
        service.replaceShapesDocumentText(
                GRAPH.datasetName(), created.getId(), read + "# theirs\n");

        assertThatThrownBy(
                        () ->
                                service.replaceShapesDocumentText(
                                        GRAPH.datasetName(),
                                        created.getId(),
                                        read + "# mine\n",
                                        SHACLDocumentUseCase.revisionOf(read)))
                .isInstanceOf(StaleWriteException.class)
                .hasMessageContaining("eq.ttl");
        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId()))
                .endsWith("# theirs\n");
    }

    @Test
    void revisionIsTheHexSha256OfTheUtf8Text() {
        // The client computes this from the text it read, so both sides must agree byte for byte.
        assertThat(SHACLDocumentUseCase.revisionOf("ä"))
                .isEqualTo("33e6d73fee82904c8d7afb78de1154d1e8dc2a0edb08120e63df5b9385c2d9cc");
    }

    @Test
    void anEmptiedDocumentReadsBackAsEmpty() {
        var created = createTurtleDocument("eq.ttl");

        // A document emptied in the editor has to be sent as something — Spring rejects an absent
        // plain-string body — so the editor sends a space. Storing that verbatim made the text read
        // back differ from the text sent, and a freshly saved document looked unsaved.
        service.replaceShapesDocumentText(GRAPH.datasetName(), created.getId(), " ");

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId())).isEmpty();
    }

    @Test
    void renameAndDisableArePersisted() {
        var created = createTurtleDocument("eq.ttl");

        var updated =
                service.updateShapesDocument(
                        GRAPH.datasetName(), created.getId(), "renamed.ttl", false, null, null);

        assertThat(updated.getName()).isEqualTo("renamed.ttl");
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void omittedFieldsAreLeftAlone() {
        var created = createTurtleDocument("eq.ttl");
        service.updateShapesDocument(GRAPH.datasetName(), created.getId(), null, false, null, null);

        var updated =
                service.updateShapesDocument(
                        GRAPH.datasetName(), created.getId(), "renamed.ttl", null, null, null);

        assertThat(updated.getName()).isEqualTo("renamed.ttl");
        assertThat(updated.isEnabled())
                .as("enabled was not passed, so it must not change")
                .isFalse();
    }

    @Test
    void reorderingKeepsThePositionsDense() {
        var first = createTurtleDocument("first.ttl");
        createTurtleDocument("second.ttl");

        service.updateShapesDocument(GRAPH.datasetName(), first.getId(), null, null, 1, null);

        assertThat(service.listShapesDocuments(GRAPH.datasetName()))
                .extracting(ShapesDocumentInfo::getOrder)
                .containsExactly(0, 1);
        assertThat(service.listShapesDocuments(GRAPH.datasetName()))
                .extracting(ShapesDocumentInfo::getName)
                .containsExactly("second.ttl", "first.ttl");
    }

    @Test
    void deletedDocumentDisappears() {
        var created = createTurtleDocument("eq.ttl");

        service.deleteShapesDocument(GRAPH.datasetName(), created.getId());

        assertThat(service.listShapesDocuments(GRAPH.datasetName())).isEmpty();
    }

    @Test
    void turtleThatDoesNotParseIsRefusedAsAClientErrorWithTheReason() {
        // Unparseable input is the caller's problem, not a server fault, and the only useful part
        // of the answer is where in their text the parser stopped.
        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(
                        () ->
                                service.replaceShapesDocumentText(
                                        GRAPH.datasetName(),
                                        createTurtleDocument("broken.ttl").getId(),
                                        "@prefix sh: <http://www.w3.org/ns/shacl#> .\n\n"
                                                + "ex:Broken a sh:NodeShape .\n"))
                .satisfies(
                        thrown -> {
                            assertThat(thrown.getStatusCode().value()).isEqualTo(400);
                            assertThat(thrown.getMessage()).contains("Undefined prefix: ex");
                            assertThat(thrown.getMessage()).contains("line: 3");
                        });
    }

    @Test
    void shapesThatViolateTheSchemaAreStillSaved() {
        // Validation findings are a report, not a gate: a half-finished document has to be
        // storable or the editor cannot be used to finish it.
        var draft = createTurtleDocument("draft.ttl");
        service.replaceShapesDocumentText(
                GRAPH.datasetName(),
                draft.getId(),
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:S a sh:NodeShape ;
                    sh:targetClass cim:NoSuchClass .
                """);

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), draft.getId()))
                .contains("cim:NoSuchClass");
    }

    /** RDF/XML may have nothing before its declaration, so this one trim has to stay. */
    @Test
    void anRdfXmlFileStartingWithABlankLineStillImports() {
        var rdfXml =
                """

                <?xml version="1.0"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/>
                """;

        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "x.rdf",
                        "x.rdf",
                        rdfXml,
                        Lang.RDFXML);

        assertThat(created.getName()).isEqualTo("x.rdf");
    }

    @Test
    void exportingASelectionIncludesOnlyTheDocumentsAskedFor() {
        var eq =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "eq.ttl",
                        null,
                        TURTLE,
                        Lang.TURTLE);
        var other =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "other.ttl",
                        null,
                        TURTLE.replace("ACLineSegmentShape", "OtherShape"),
                        Lang.TURTLE);

        var exported =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(),
                                RDFFormat.TURTLE,
                                List.of(eq.getId()),
                                List.of())
                        .toString(StandardCharsets.UTF_8);

        assertThat(exported).contains("ACLineSegmentShape").doesNotContain("OtherShape");
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void aDisabledDocumentIsStillExportedWhenItIsAskedForByName() {
        // Disabling says "take no part in validation", not "never let me export you".
        var draft =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "draft.ttl",
                        null,
                        TURTLE,
                        Lang.TURTLE);
        service.updateShapesDocument(GRAPH.datasetName(), draft.getId(), null, false, null, null);

        var exported =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(),
                                RDFFormat.TURTLE,
                                List.of(draft.getId()),
                                List.of())
                        .toString(StandardCharsets.UTF_8);

        assertThat(exported).contains("ACLineSegmentShape");
    }

    @Test
    void anEmptyDocumentTickedAlongsideDoesNotStopAFileComingBackAsWritten() {
        var empty =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "empty.ttl",
                        null,
                        "",
                        Lang.TURTLE);
        var eq = createTurtleDocument("eq.ttl");

        var exported =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(),
                                RDFFormat.TURTLE,
                                List.of(empty.getId(), eq.getId()),
                                List.of())
                        .toString(StandardCharsets.UTF_8);

        assertThat(exported).isEqualTo(TURTLE);
    }

    @Test
    void exportingNothingProducesAnEmptyDocumentRatherThanFailing() {
        var exported =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(), RDFFormat.TURTLE, List.of(), List.of())
                        .toString(StandardCharsets.UTF_8);

        assertThat(exported).doesNotContain("targetClass");
    }

    @Test
    void anIdThatNamesNoDocumentIsNotFoundRatherThanSilentlyLeftOut() {
        // An export missing a file the user ticked looks complete; saying so is the only warning.
        var eq =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "eq.ttl",
                        null,
                        TURTLE,
                        Lang.TURTLE);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(
                        () ->
                                service.exportSelectedSHACLGraph(
                                        GRAPH.datasetName(),
                                        RDFFormat.TURTLE,
                                        List.of(eq.getId(), UUID.randomUUID()),
                                        List.of()));
    }

    @Test
    void duplicateNamesAreRefused() {
        createTurtleDocument("eq.ttl");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> createTurtleDocument("eq.ttl"));
    }

    @Test
    void renamingOntoAnExistingNameIsRefusedButKeepingOwnNameIsFine() {
        createTurtleDocument("eq.ttl");
        var tp = createTurtleDocument("tp.ttl");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.updateShapesDocument(
                                        GRAPH.datasetName(),
                                        tp.getId(),
                                        "eq.ttl",
                                        null,
                                        null,
                                        null));

        var unchanged =
                service.updateShapesDocument(
                        GRAPH.datasetName(), tp.getId(), "tp.ttl", null, null, null);
        assertThat(unchanged.getName()).isEqualTo("tp.ttl");
    }

    @Test
    void aReadOnlyWorkspaceRefusesEveryWriteToItsDocuments() {
        var created = createTurtleDocument("official.ttl");
        var id = created.getId();
        databasePort.disableEditing(GRAPH.datasetName());

        assertThatThrownBy(
                        () ->
                                service.createShapesDocument(
                                        GRAPH.datasetName(),
                                        List.of(GRAPH.graphUri()),
                                        "new.ttl",
                                        null,
                                        "",
                                        Lang.TURTLE))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> service.replaceShapesDocumentText(GRAPH.datasetName(), id, ""))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(
                        () ->
                                service.updateShapesDocument(
                                        GRAPH.datasetName(), id, "x", false, 0, null))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.deleteShapesDocument(GRAPH.datasetName(), id))
                .isInstanceOf(ResourceConflictException.class);

        assertThat(service.listShapesDocuments(GRAPH.datasetName()))
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.getName()).isEqualTo("official.ttl");
                            assertThat(info.isEnabled()).isTrue();
                            assertThat(info.getTripleCount()).isEqualTo(created.getTripleCount());
                        });
    }

    @Test
    void documentsStayOutOfWhatTheCustomShaclPathReads() {
        createTurtleDocument("eq.ttl");

        var custom =
                service.exportCustomSHACLGraph(GRAPH, RDFFormat.TURTLE)
                        .toString(StandardCharsets.UTF_8);
        var combined =
                service.exportCombinedSHACLGraph(GRAPH, RDFFormat.TURTLE)
                        .toString(StandardCharsets.UTF_8);

        assertThat(custom).doesNotContain("ACLineSegmentShape");
        assertThat(combined).doesNotContain("ACLineSegmentShape");
    }

    @Test
    void theCustomShaclPathStaysOutOfTheDocuments() {
        var shacl = GraphFactory.createDefaultGraph();
        shacl.add(
                Triple.create(
                        NodeFactory.createURI("http://ex.org/CustomShape"),
                        NodeFactory.createURI("http://www.w3.org/ns/shacl#targetClass"),
                        NodeFactory.createURI("http://ex.org/Thing")));

        service.replaceCustomSHACLGraph(GRAPH, shacl);

        assertThat(service.listShapesDocuments(GRAPH.datasetName())).isEmpty();
    }

    @Test
    void unknownDocumentIsReportedAsNotFound() {
        var missing = UUID.randomUUID();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getShapesDocumentText(GRAPH.datasetName(), missing));
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteShapesDocument(GRAPH.datasetName(), missing));
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(
                        () ->
                                service.replaceShapesDocumentText(
                                        GRAPH.datasetName(), missing, TURTLE));
    }

    // -------------------------------------------------------------------------
    // Which schemas a document applies to
    // -------------------------------------------------------------------------

    private static final String OTHER_SCHEMA = "http://example.org/other";

    private void givenOtherSchema() {
        try (var transaction =
                databasePort.beginTransaction(GRAPH.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(OTHER_SCHEMA, GraphFactory.createDefaultGraph());
            transaction.commit("Created another schema");
        }
    }

    @Test
    void aDocumentCanApplyToSeveralSchemasAtOnce() {
        givenOtherSchema();

        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri(), OTHER_SCHEMA),
                        "both.ttl",
                        null,
                        TURTLE,
                        Lang.TURTLE);

        assertThat(created.getSchemas()).containsExactlyInAnyOrder(GRAPH.graphUri(), OTHER_SCHEMA);
    }

    @Test
    void aDocumentCanApplyToNoSchema() {
        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(), List.of(), "none.ttl", null, TURTLE, Lang.TURTLE);

        assertThat(created.getSchemas()).isEmpty();
    }

    @Test
    void aSchemaThatDoesNotExistIsRefusedAndNothingIsCreated() {
        assertThatThrownBy(
                        () ->
                                service.createShapesDocument(
                                        GRAPH.datasetName(),
                                        List.of("http://example.org/nowhere"),
                                        "nowhere.ttl",
                                        null,
                                        TURTLE,
                                        Lang.TURTLE))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(service.listShapesDocuments(GRAPH.datasetName())).isEmpty();
    }

    @Test
    void anUpdateReplacesTheSchemasAndLeavesThemWhenNotAsked() {
        givenOtherSchema();
        var created = createTurtleDocument("document.ttl");

        var unchanged =
                service.updateShapesDocument(
                        GRAPH.datasetName(), created.getId(), null, false, null, null);
        assertThat(unchanged.getSchemas()).containsExactly(GRAPH.graphUri());

        var moved =
                service.updateShapesDocument(
                        GRAPH.datasetName(),
                        created.getId(),
                        null,
                        null,
                        null,
                        List.of(OTHER_SCHEMA));
        assertThat(moved.getSchemas()).containsExactly(OTHER_SCHEMA);

        var cleared =
                service.updateShapesDocument(
                        GRAPH.datasetName(), created.getId(), null, null, null, List.of());
        assertThat(cleared.getSchemas()).isEmpty();
    }

    @Test
    void anUpdateToASchemaThatDoesNotExistIsRefused() {
        var created = createTurtleDocument("document.ttl");

        assertThatThrownBy(
                        () ->
                                service.updateShapesDocument(
                                        GRAPH.datasetName(),
                                        created.getId(),
                                        null,
                                        null,
                                        null,
                                        List.of("http://example.org/nowhere")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDocumentNameIsUniqueAcrossTheWorkspaceWhicheverSchemasItAppliesTo() {
        givenOtherSchema();
        createTurtleDocument("document.ttl");

        assertThatThrownBy(
                        () ->
                                service.createShapesDocument(
                                        GRAPH.datasetName(),
                                        List.of(OTHER_SCHEMA),
                                        "document.ttl",
                                        null,
                                        TURTLE,
                                        Lang.TURTLE))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void generatedShapesAreMergedInForEachSchemaAsked() {
        var created = createTurtleDocument("document.ttl");

        var alone =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(),
                                RDFFormat.NTRIPLES,
                                List.of(created.getId()),
                                List.of())
                        .toString(StandardCharsets.UTF_8);
        var merged =
                service.exportSelectedSHACLGraph(
                                GRAPH.datasetName(),
                                RDFFormat.NTRIPLES,
                                List.of(created.getId()),
                                List.of(GRAPH.graphUri()))
                        .toString(StandardCharsets.UTF_8);

        assertThat(merged).contains(alone.lines().toList());
    }

    @Test
    void generatedShapesForASchemaThatDoesNotExistAreRefused() {
        assertThatThrownBy(
                        () ->
                                service.exportSelectedSHACLGraph(
                                        GRAPH.datasetName(),
                                        RDFFormat.TURTLE,
                                        List.of(),
                                        List.of("http://example.org/nowhere")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
