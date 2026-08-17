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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static utils.TestUtils.readMultipartFileFromFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ExpandURIService;
import org.rdfarchitect.services.update.classes.CopyClassSource;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

@SpringBootTest
class CopyClassSourceReaderTest {

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";
    private static final String UNKNOWN_CLASS_UUID = "0f0e0d0c-0b0a-4988-8766-554433221100";

    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    private DatabasePort databasePort;
    private CopyClassSourceReader sourceReader;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort =
                spy(new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig())));
        sourceReader = new CopyClassSourceReader(databasePort, new ExpandURIService(databasePort));

        var file = readMultipartFileFromFile(PATH, "class.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    private List<CopyClassSource> sources(String classUUID) {
        return List.of(new CopyClassSource(graphIdentifier, UUID.fromString(classUUID)));
    }

    @Test
    void readSources_sharedSnapshots_snapshotsTheSourceGraphOnce() {
        var snapshots = sourceReader.newSourceSnapshots();

        sourceReader.readSources(sources(CLASS_UUID), snapshots);
        sourceReader.readSources(sources(CLASS_UUID), snapshots);

        verify(databasePort, times(1)).getGraphWithContext(graphIdentifier);
    }

    @Test
    void readSources_ownSnapshots_snapshotsTheSourceGraphPerCall() {
        sourceReader.readSources(sources(CLASS_UUID));
        sourceReader.readSources(sources(CLASS_UUID));

        verify(databasePort, times(2)).getGraphWithContext(graphIdentifier);
    }

    @Test
    void readSources_classIsNotInTheGraph_skipsIt() {
        var resolved = sourceReader.readSources(sources(UNKNOWN_CLASS_UUID));

        assertThat(resolved).isEmpty();
    }
}
