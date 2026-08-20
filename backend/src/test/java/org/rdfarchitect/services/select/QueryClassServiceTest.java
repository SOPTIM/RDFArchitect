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

package org.rdfarchitect.services.select;

import static org.assertj.core.api.Assertions.assertThat;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class QueryClassServiceTest {

    private QueryClassService queryClassService;
    private DatabasePort databasePort;
    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    @Autowired private ClassUMLAdaptedMapper umlAdaptedClassMapper;
    @Autowired private ClassMapper classMapper;
    @Autowired private SuperClassResolver superClassResolver;

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final String CLASS_UUID = "43836908-c7f7-4749-bb8b-3ac9250de655";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        queryClassService =
                new QueryClassService(
                        databasePort, umlAdaptedClassMapper, classMapper, superClassResolver);
        var file = readMultipartFileFromFile(PATH, "class.ttl");
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    @Test
    @DisplayName("reports a class that is only referenced by other resources as external")
    void getClassInformation_referencedOnlyResource_returnsExternalClass() {
        var referencedUuid = addReferencedOnlyResource("ghost");

        var classDTO =
                queryClassService.getClassInformation(
                        graphIdentifier, referencedUuid.toString(), true);

        assertThat(classDTO).isNotNull();
        assertThat(classDTO.getExternal()).isTrue();
        assertThat(classDTO.getUuid()).isEqualTo(referencedUuid);
        assertThat(classDTO.getPrefix()).isEqualTo(PREFIX);
        assertThat(classDTO.getLabel()).isEqualTo("ghost");
        assertThat(classDTO.getStereotypes()).isEmpty();
    }

    @Test
    @DisplayName("does not mark a class defined in the graph as external")
    void getClassInformation_definedClass_doesNotSetExternal() {
        var classDTO = queryClassService.getClassInformation(graphIdentifier, CLASS_UUID, true);

        assertThat(classDTO).isNotNull();
        assertThat(classDTO.getExternal()).isNull();
        assertThat(classDTO.getLabel()).isEqualTo("oldLabel");
    }

    @Test
    @DisplayName("returns nothing for a uuid that no resource carries")
    void getClassInformation_unknownUuid_returnsNull() {
        var classDTO =
                queryClassService.getClassInformation(
                        graphIdentifier, UUID.randomUUID().toString(), true);

        assertThat(classDTO).isNull();
    }

    /** Referencing a uri that nothing defines makes it a referenced only resource with a uuid. */
    private UUID addReferencedOnlyResource(String label) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph()
                    .add(
                            NodeFactory.createURI(PREFIX + "class.associatedClass"),
                            RDFS.range.asNode(),
                            NodeFactory.createURI(PREFIX + label));
            ctx.commit("referenced only resource");
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            return UUID.fromString(
                    model.getResource(PREFIX + label).getProperty(RDFA.uuid).getString());
        }
    }
}
