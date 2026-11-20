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

package org.rdfarchitect.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import java.nio.file.Path;
import java.util.UUID;

class WorkspaceFileLocationsTest {

    private static final Path FILE = Path.of("/data/Draft.rdfa");
    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    private DatabasePort databasePort;
    private WorkspaceFileLocations locations;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        locations = new WorkspaceFileLocations(databasePort);
        databasePort.createDataset("Draft");
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private void addSchema(String schemaName) {
        try (var transaction = databasePort.beginTransaction("Draft", ReadWrite.WRITE)) {
            transaction.createGraph(
                    schemaName, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.commit("Add " + schemaName);
        }
    }

    @Test
    void keepsTheFileWhileTheWorkspaceGoesOn() {
        addSchema("a");
        locations.remember("Draft", FILE, databasePort.currentChangeId("Draft"));

        addSchema("b");
        databasePort.undo("Draft");
        databasePort.undo("Draft");

        assertThat(locations.locationOf("Draft")).contains(FILE);
        assertThat(locations.workspacesWithFile()).containsExactly("Draft");
    }

    @Test
    void doesNotHandTheFileToAWorkspaceCreatedAnewUnderTheSameName() {
        addSchema("a");
        locations.remember("Draft", FILE, databasePort.currentChangeId("Draft"));

        databasePort.deleteDataset("Draft");
        databasePort.createDataset("Draft");
        addSchema("a");

        assertThat(locations.locationOf("Draft")).isEmpty();
    }

    @Test
    void forgetsTheFileOfADeletedWorkspace() {
        locations.remember("Draft", FILE, databasePort.currentChangeId("Draft"));

        databasePort.deleteDataset("Draft");

        assertThat(locations.locationOf("Draft")).isEmpty();
    }

    @Test
    void forgetsAFileReplacedByAnother() {
        locations.remember("Draft", FILE, databasePort.currentChangeId("Draft"));

        locations.forget("Draft");

        assertThat(locations.locationOf("Draft")).isEmpty();
        assertThat(locations.workspacesWithFile()).isEmpty();
    }

    @Test
    void keepsFilesPerSession() {
        locations.remember("Draft", FILE, databasePort.currentChangeId("Draft"));

        SessionContext.setSessionId("another session");

        assertThat(locations.workspacesWithFile()).isEmpty();
        assertThat(locations.locationOf("Draft")).isEmpty();
    }
}
