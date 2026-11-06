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

package org.rdfarchitect.services.archive.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.rdfarchitect.services.archive.ArchiveFiles.json;
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;
import static org.rdfarchitect.services.archive.ArchiveFiles.zip;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.archive.ArchiveFiles;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;
import org.rdfarchitect.services.update.graph.ImportProgressListener;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

class ImportWorkspaceServiceTest {

    private static final String CORE = "http://example.org/graphs/core";
    private static final String EXTENSION = "http://example.org/graphs/extension";

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    private DatabasePort databasePort;
    private ImportWorkspaceService service;
    private byte[] archive;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        service = new ImportWorkspaceService(databasePort);
        databasePort.createDataset("original");
        try (var transaction = databasePort.beginTransaction("original", ReadWrite.WRITE)) {
            transaction.createGraph(CORE, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.createGraph(EXTENSION, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.setPrefixes(
                    new PrefixMappingImpl()
                            .setNsPrefixes(Map.of("only", "http://example.org/only#")));
            transaction.commit("Set up the workspace");
        }
        databasePort.disableEditing("original");
        archive =
                new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"))
                        .exportWorkspace("original");
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void importWorkspace_createsTheWorkspaceUnderTheGivenName() {
        var result = importAs("restored", archive, ImportProgressListener.NOOP);

        assertThat(databasePort.listDatasets()).contains("restored");
        assertThat(databasePort.listGraphUris("restored"))
                .containsExactlyInAnyOrder(CORE, EXTENSION);
        assertThat(result.importedGraphUris()).containsExactlyInAnyOrder(CORE, EXTENSION);
    }

    @Test
    void importWorkspace_restoresExactlyThePrefixesOfTheWorkspace() {
        importAs("restored", archive, ImportProgressListener.NOOP);

        assertThat(databasePort.getPrefixMapping("restored").getNsPrefixMap())
                .isEqualTo(Map.of("only", "http://example.org/only#"));
    }

    @Test
    void importWorkspace_restoresTheReadOnlyFlag() {
        importAs("restored", archive, ImportProgressListener.NOOP);

        assertThat(databasePort.isReadOnly("restored")).isTrue();
    }

    @Test
    void importWorkspace_reportsOneStepPerGraph() {
        var events = new ArrayList<String>();
        var listener =
                new ImportProgressListener() {
                    @Override
                    public void planned(List<PlannedImport> plannedImports) {
                        plannedImports.forEach(
                                planned -> events.add("planned " + planned.fileName()));
                    }

                    @Override
                    public void finished(int index, Outcome outcome, String graphUri) {
                        events.add(outcome + " " + graphUri);
                    }
                };

        importAs("restored", archive, listener);

        assertThat(events)
                .containsExactlyInAnyOrder(
                        "planned " + CORE,
                        "planned " + EXTENSION,
                        "IMPORTED " + CORE,
                        "IMPORTED " + EXTENSION);
    }

    @Test
    void importWorkspace_existingName_leavesThatWorkspaceAlone() {
        databasePort.createDataset("taken");

        assertThatThrownBy(() -> importAs("taken", archive, ImportProgressListener.NOOP))
                .isInstanceOf(ResourceConflictException.class);
        assertThat(databasePort.listGraphUris("taken")).isEmpty();
    }

    @Test
    void importWorkspace_invalidArchive_createsNoWorkspace() {
        var files = unzip(archive);
        files.remove(WorkspaceArchiveFormat.COLORS);

        assertThatThrownBy(() -> importAs("restored", zip(files), ImportProgressListener.NOOP))
                .isInstanceOf(InvalidWorkspaceArchiveException.class);
        assertThat(databasePort.listDatasets()).doesNotContain("restored");
    }

    @Test
    void importWorkspace_cancelled_keepsNothing() {
        var outcomes = new TreeMap<Integer, Outcome>();
        var listener =
                new ImportProgressListener() {
                    @Override
                    public void finished(int index, Outcome outcome, String graphUri) {
                        outcomes.put(index, outcome);
                    }

                    @Override
                    public boolean isCancelled() {
                        return !outcomes.isEmpty();
                    }
                };

        var result = importAs("restored", archive, listener);

        assertThat(result.importedGraphUris()).isEmpty();
        assertThat(databasePort.listDatasets()).doesNotContain("restored");
        assertThat(outcomes.values()).containsOnly(Outcome.SKIPPED);
    }

    @Test
    void importWorkspace_failingHalfWay_keepsNothingAndReportsNothingAsImported() {
        var outcomes = new TreeMap<Integer, Outcome>();
        var listener =
                new ImportProgressListener() {
                    @Override
                    public void started(int index) {
                        if (index == 1) {
                            throw new IllegalStateException("broke down");
                        }
                    }

                    @Override
                    public void finished(int index, Outcome outcome, String graphUri) {
                        outcomes.put(index, outcome);
                    }
                };

        assertThatThrownBy(() -> importAs("restored", archive, listener)).hasMessage("broke down");
        assertThat(databasePort.listDatasets()).doesNotContain("restored");
        assertThat(outcomes.values()).containsOnly(Outcome.SKIPPED);
    }

    @Test
    void importWorkspace_cancelledAfterTheLastGraph_keepsNothing() {
        var outcomes = new TreeMap<Integer, Outcome>();
        var listener =
                new ImportProgressListener() {
                    @Override
                    public void finished(int index, Outcome outcome, String graphUri) {
                        outcomes.put(index, outcome);
                    }

                    @Override
                    public boolean isCancelled() {
                        return outcomes.size() == 2;
                    }
                };

        var result = importAs("restored", archive, listener);

        assertThat(result.importedGraphUris()).isEmpty();
        assertThat(databasePort.listDatasets()).doesNotContain("restored");
        assertThat(outcomes.values()).containsOnly(Outcome.SKIPPED);
    }

    @Test
    void importWorkspace_passesOnWhatTheUserShouldKnow() {
        var files = unzip(archive);
        var manifest = json(files.get(WorkspaceArchiveFormat.MANIFEST));
        manifest.put("formatVersion", "1.9");
        files.put(WorkspaceArchiveFormat.MANIFEST, ArchiveFiles.json(manifest));

        var result = importAs("restored", zip(files), ImportProgressListener.NOOP);

        assertThat(result.notices()).singleElement().asString().contains("format 1.9");
    }

    private ImportWorkspaceUseCase.WorkspaceImportResult importAs(
            String name, byte[] content, ImportProgressListener listener) {
        return service.importWorkspace(name, new ByteArrayInputStream(content), listener);
    }
}
