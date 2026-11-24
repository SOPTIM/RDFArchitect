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

package org.rdfarchitect.services.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.services.archive.ArchiveFiles.assertContainsEverythingOf;
import static org.rdfarchitect.services.archive.ArchiveFiles.json;
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;

import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;
import org.rdfarchitect.services.archive.importing.ImportWorkspaceService;
import org.rdfarchitect.services.archive.importing.ImportWorkspaceUseCase;
import org.rdfarchitect.services.update.graph.ImportProgressListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** Every release has to import this archive: never regenerate it. Deleting this test drops 1.0. */
class WorkspaceArchiveFormat10CompatibilityTest {

    private static final String ARCHIVE = "workspace-format-1.0.rdfa";

    private DatabasePort databasePort;
    private byte[] archive;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        try (var in = getClass().getResourceAsStream(ARCHIVE)) {
            archive = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void importsWithoutNotices() {
        var result = importArchive();

        assertThat(result.notices()).isEmpty();
        assertThat(result.importedSchemaNames())
                .containsExactly(
                        "61970-600-2_Header-AP-Voc-RDFS2020",
                        "61970-600-2_Topology-AP-Voc-RDFS2020");
    }

    @Test
    void turnsTheConstraintsOfEachSchemaIntoADocumentApplyingToIt() {
        importArchive();

        var schemaName = "61970-600-2_Header-AP-Voc-RDFS2020";
        var written = unzip(archive).get("schemas/" + schemaName + "/shacl.ttl");
        try (var transaction = databasePort.beginTransaction("imported", ReadWrite.READ)) {
            assertThat(transaction.shapesDocuments().values())
                    .singleElement()
                    .satisfies(
                            document -> {
                                assertThat(document.getName()).isEqualTo(schemaName + "-shacl.ttl");
                                assertThat(document.getSchemas()).containsExactly(schemaName);
                                assertThat(document.isEnabled()).isTrue();
                                assertThat(document.getRawText()).isEqualTo(written);
                                assertThat(
                                                document.getGraph()
                                                        .isIsomorphicWith(
                                                                ArchiveFiles.turtle(written)))
                                        .isTrue();
                            });
        }
    }

    @Test
    void keepsEverythingTheArchiveHolds() {
        importArchive();

        var reexported =
                new ExportWorkspaceService(databasePort, new AppVersionResolver("test"))
                        .exportWorkspace("imported");

        assertContainsEverythingOf(unzip(archive), unzip(reexported));
    }

    @Test
    void keepsTheIdsTheLayoutsAreStoredUnder() {
        importArchive();

        var files = unzip(archive);
        var manifest = json(files.get(WorkspaceArchiveFormat.MANIFEST));
        var workspaceDiagrams = json(files.get("diagrams.json"));
        try (var transaction = databasePort.beginTransaction("imported", ReadWrite.READ)) {
            assertThat(transaction.crossProfileDiagramId())
                    .hasToString(workspaceDiagrams.get("crossProfileDiagramId").asText());
            assertThat(transaction.layout().getDefaultPackageMRID().getUuid())
                    .hasToString(workspaceDiagrams.get("defaultPackageMRID").asText());
            for (var schema : manifest.get("schemas")) {
                var schemaName = schema.asText();
                var graphDiagrams = json(files.get("schemas/" + schemaName + "/diagrams.json"));
                assertThat(
                                transaction
                                        .graph(schemaName)
                                        .getDiagramLayout()
                                        .getDefaultPackageMRID()
                                        .getUuid())
                        .hasToString(graphDiagrams.get("defaultPackageMRID").asText());
            }
        }
    }

    @Test
    void drawsThePackageDiagramsWhereTheyWereLaidOut() {
        importArchive();

        var positions =
                new CgmesWorkspace(databasePort)
                        .positionsOfFirstPackage("imported", "61970-600-2_Header-AP-Voc-RDFS2020");

        assertThat(positions).isNotEmpty().containsValue("100.0,50.0");
    }

    private ImportWorkspaceUseCase.WorkspaceImportResult importArchive() {
        return new ImportWorkspaceService(databasePort)
                .importWorkspace(
                        "imported",
                        new java.io.ByteArrayInputStream(archive),
                        ImportProgressListener.NOOP);
    }
}
