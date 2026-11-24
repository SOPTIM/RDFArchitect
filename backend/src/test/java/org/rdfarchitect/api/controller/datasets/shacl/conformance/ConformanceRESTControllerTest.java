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

package org.rdfarchitect.api.controller.datasets.shacl.conformance;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.handlers.GenericExceptionHandler;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.services.shacl.conformance.ConformanceService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/** The HTTP contract of the conformance report, answered by the real service. */
class ConformanceRESTControllerTest {

    private static final String URL = "/api/workspaces/cgmes/shacl/conformance";

    private static final String SCHEMA = "Core Equipment";

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            ex:LineShape a sh:NodeShape ;
                sh:targetClass ex:Line ;
                sh:property [ sh:path ex:Line.length ; sh:minCount 1 ] .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createDataset("cgmes");
        DatabaseFixtures.createGraph(
                databasePort,
                new GraphIdentifier("cgmes", SCHEMA),
                GraphFactory.createDefaultGraph());
        new SHACLStoringService(databasePort)
                .createShapesDocument(
                        "cgmes", List.of(SCHEMA), "line.ttl", null, SHAPES, Lang.TURTLE);

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ConformanceRESTController(new ConformanceService(databasePort)))
                        .setControllerAdvice(new GenericExceptionHandler())
                        .build();
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void theReportIsServedAsJsonForTheNamedSchema() throws Exception {
        mockMvc.perform(get(URL).param("schema", SCHEMA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.schemaName").value(SCHEMA))
                .andExpect(jsonPath("$.documents[0].name").value("line.ttl"))
                .andExpect(jsonPath("$.findings[0].kind").value("NOT_IN_SCHEMA"))
                .andExpect(jsonPath("$.findings[0].statedIn[0]").value("line.ttl"));
    }

    @Test
    void comparingWithoutNamingASchemaIsRefused() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
    }

    @Test
    void aNameNoSchemaCanHaveIsRefused() throws Exception {
        mockMvc.perform(get(URL).param("schema", "a;b"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not a valid schema name")));
    }

    @Test
    void aSchemaTheWorkspaceDoesNotHaveIsNotFound() throws Exception {
        mockMvc.perform(get(URL).param("schema", "nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("No schema named nowhere.")));
    }

    @Test
    void aWorkspaceThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(get("/api/workspaces/elsewhere/shacl/conformance").param("schema", SCHEMA))
                .andExpect(status().isNotFound());
    }
}
