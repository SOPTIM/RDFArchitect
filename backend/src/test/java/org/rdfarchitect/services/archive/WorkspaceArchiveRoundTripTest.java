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
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;
import org.rdfarchitect.services.archive.importing.ImportWorkspaceService;
import org.rdfarchitect.services.update.graph.ImportProgressListener;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

class WorkspaceArchiveRoundTripTest {

    private DatabasePort databasePort;
    private CgmesWorkspace cgmes;
    private ExportWorkspaceService exportService;
    private ImportWorkspaceService importService;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        cgmes = new CgmesWorkspace(databasePort);
        exportService = new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"));
        importService = new ImportWorkspaceService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void exportImportExport_givesTheSameArchive() {
        cgmes.build(
                "original",
                List.of(
                        CgmesWorkspace.HEADER,
                        CgmesWorkspace.TOPOLOGY,
                        CgmesWorkspace.DIAGRAM_LAYOUT));
        var exported = exportService.exportWorkspace("original");

        importService.importWorkspace(
                "restored", new ByteArrayInputStream(exported), ImportProgressListener.NOOP);
        var reexported = unzip(exportService.exportWorkspace("restored"));

        var original = unzip(exported);
        assertThat(reexported.keySet()).isEqualTo(original.keySet());
        assertContainsEverythingOf(original, reexported);
    }

    @Test
    void restoredWorkspace_drawsItsDiagramsWhereTheOriginalDid() {
        var graphUris =
                cgmes.build("original", List.of(CgmesWorkspace.HEADER, CgmesWorkspace.TOPOLOGY));
        var exported = exportService.exportWorkspace("original");

        importService.importWorkspace(
                "restored", new ByteArrayInputStream(exported), ImportProgressListener.NOOP);

        for (var graphUri : graphUris) {
            assertThat(cgmes.positionsOfFirstPackage("restored", graphUri))
                    .as(graphUri)
                    .isNotEmpty()
                    .isEqualTo(cgmes.positionsOfFirstPackage("original", graphUri));
        }
    }

    @Test
    void restoredWorkspace_hasNothingToUndo() {
        cgmes.build("original", List.of(CgmesWorkspace.HEADER));
        var exported = exportService.exportWorkspace("original");

        importService.importWorkspace(
                "restored", new ByteArrayInputStream(exported), ImportProgressListener.NOOP);

        databasePort.createDataset("fresh");
        assertThat(databasePort.canUndo("restored")).isFalse();
        assertThat(databasePort.listChanges("restored"))
                .as("the history of a workspace that was just created")
                .extracting(WorkspaceChangeLogEntry::message)
                .isEqualTo(
                        databasePort.listChanges("fresh").stream()
                                .map(WorkspaceChangeLogEntry::message)
                                .toList());
    }
}
