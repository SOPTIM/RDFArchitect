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
        assertThat(result.importedGraphUris())
                .containsExactly(
                        "http://graph#61970_600_2_Topology_AP_Voc_RDFS2020",
                        "http://graph#61970_600_2_Header_AP_Voc_RDFS2020");
    }

    @Test
    void keepsEverythingTheArchiveHolds() {
        importArchive();

        var reexported =
                new ExportWorkspaceService(databasePort, new AppVersionResolver("test"))
                        .exportWorkspace("restored");

        assertContainsEverythingOf(unzip(archive), unzip(reexported));
    }

    @Test
    void restoresTheIdsTheLayoutsAreStoredUnder() {
        importArchive();

        var files = unzip(archive);
        var manifest = json(files.get(WorkspaceArchiveFormat.MANIFEST));
        var workspaceDiagrams = json(files.get("diagrams.json"));
        try (var transaction = databasePort.beginTransaction("restored", ReadWrite.READ)) {
            assertThat(transaction.crossProfileDiagramId())
                    .hasToString(workspaceDiagrams.get("crossProfileDiagramId").asText());
            assertThat(transaction.layout().getDefaultPackageMRID().getUuid())
                    .hasToString(workspaceDiagrams.get("defaultPackageMRID").asText());
            for (var graph : manifest.get("graphs")) {
                var graphDiagrams =
                        json(
                                files.get(
                                        "graphs/"
                                                + graph.get("folder").asText()
                                                + "/diagrams.json"));
                assertThat(
                                transaction
                                        .graph(graph.get("uri").asText())
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
                        .positionsOfFirstPackage(
                                "restored", "http://graph#61970_600_2_Header_AP_Voc_RDFS2020");

        assertThat(positions).isNotEmpty().containsValue("100.0,50.0");
    }

    private ImportWorkspaceUseCase.WorkspaceImportResult importArchive() {
        return new ImportWorkspaceService(databasePort)
                .importWorkspace(
                        "restored",
                        new java.io.ByteArrayInputStream(archive),
                        ImportProgressListener.NOOP);
    }
}
