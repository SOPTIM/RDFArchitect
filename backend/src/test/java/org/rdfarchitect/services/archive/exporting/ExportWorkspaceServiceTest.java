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

package org.rdfarchitect.services.archive.exporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.services.archive.ArchiveFiles.json;
import static org.rdfarchitect.services.archive.ArchiveFiles.tree;
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;

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
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ExportWorkspaceServiceTest {

    private static final String CORE = "http://example.org/graphs/core";
    private static final String FOLDER = "example.org-graphs-core";

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    private final UUID classUuid = UUID.randomUUID();
    private final UUID diagramId = UUID.randomUUID();

    private DatabasePort databasePort;
    private ExportWorkspaceService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        service = new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"));
        databasePort.createDataset("Team/Draft");
        try (var transaction = databasePort.beginTransaction("Team/Draft", ReadWrite.WRITE)) {
            transaction.createGraph(CORE, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.setPrefixes(
                    new PrefixMappingImpl()
                            .setNsPrefixes(Map.of("only", "http://example.org/only#")));
            var diagram =
                    new CustomDiagram(
                            diagramId,
                            "Core",
                            List.of(new ClassInDiagram(classUuid, new URI(CORE))));
            transaction.graph(CORE).getCustomDiagrams().put(diagramId, diagram);
            transaction.crossProfileColors().setColor(CORE, "#abcdef");
            transaction.commit("Set up the workspace");
        }
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void manifest_describesTheWorkspace() {
        databasePort.disableEditing("Team/Draft");

        var manifest =
                json(
                        unzip(service.exportWorkspace("Team/Draft"))
                                .get(WorkspaceArchiveFormat.MANIFEST));

        assertThat(manifest.get("formatVersion").asText())
                .isEqualTo(WorkspaceArchiveFormat.FORMAT_VERSION);
        assertThat(manifest.get("appVersion").asText()).isEqualTo("1.2.3");
        assertThat(manifest.get("name").asText()).isEqualTo("Team/Draft");
        assertThat(manifest.get("readOnly").asBoolean()).isTrue();
        assertThat(manifest.get("graphs"))
                .isEqualTo(tree("[{\"folder\": \"%s\", \"uri\": \"%s\"}]".formatted(FOLDER, CORE)));
    }

    @Test
    void namespaces_haveAFileOfTheirOwn() {
        var namespaces = unzip(service.exportWorkspace("Team/Draft")).get("namespaces.json");

        assertThat(tree(namespaces)).isEqualTo(tree("{\"only\": \"http://example.org/only#\"}"));
    }

    @Test
    void graphDiagrams_listTheirClassesByUuid_andColorsReferToGraphsByFolder() {
        var files = unzip(service.exportWorkspace("Team/Draft"));

        assertThat(json(files.get("graphs/" + FOLDER + "/diagrams.json")).get("customDiagrams"))
                .isEqualTo(
                        tree(
                                "[{\"diagramId\": \"%s\", \"name\": \"Core\", \"classes\": [\"%s\"]}]"
                                        .formatted(diagramId, classUuid)));
        assertThat(tree(files.get("colors.json")))
                .isEqualTo(tree("{\"%s\": \"#abcdef\"}".formatted(FOLDER)));
        assertThat(json(files.get("diagrams.json")).get("customDiagrams")).isEmpty();
    }
}
