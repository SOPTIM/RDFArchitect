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

package org.rdfarchitect.cim.data.queries.update.cimupdates;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.TxnType;
import org.apache.jena.update.Update;
import org.junit.jupiter.api.BeforeAll;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutioner;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public class CIMUpdatesTestBase {

    private static final String PATH = "src/test/java/org/rdfarchitect/cim/data/queries/update/cimupdates/";
    private static final String BASE_FILENAME = "base.ttl";
    protected static final String GRAPH_URI = "http://graph";
    protected static final GraphIdentifier graphIdentifier = new GraphIdentifier("default", GRAPH_URI);
    protected static GraphRewindableWithUUIDs testGraph;
    protected static DatabasePort databasePort;

    //base test constants
    protected static final String DATASET_NAME = "default";
    protected static final String URI_PREFIX = "http://example.com#";
    protected static final String OTHER_URI_PREFIX = "http://other.org#";
    protected static final UUID MY_UUID = UUID.fromString("43836908-c7f7-4749-bb8b-3ac9250de655");
    protected static final String CLASS_LABEL = "class";
    protected static final String COMMENT_FORMAT = "http://www.w3.org/2001/XMLSchema#string";
    protected static final String ENUM_ENTRY_LABEL = "enumEntry";
    protected static final String PACKAGE_LABEL = "package";
    protected static final String OTHER_PACKAGE_LABEL = "otherPackage";
    protected static final String ATTRIBUTE_LABEL = "attribute";
    protected static final String COMMENT = "This is a comment";
    protected static final String IS_FIXED_VALUE = "isFixed";
    protected static final String IS_DEFAULT_VALUE = "isDefault";
    protected static final String MULTIPLICITY_URI = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:1..1";

    //classes
    protected static final String CLASS_URI = URI_PREFIX + CLASS_LABEL;
    protected static final String EXISTING_CLASS_LABEL = "existing_" + CLASS_LABEL;
    protected static final String EXISTING_CLASS_URI = URI_PREFIX + EXISTING_CLASS_LABEL;
    protected static final String SUB_CLASS_LABEL = "sub_" + CLASS_LABEL;
    protected static final String SUB_CLASS_URI = URI_PREFIX + SUB_CLASS_LABEL;
    protected static final String SUPER_CLASS_LABEL = "super_" + CLASS_LABEL;
    protected static final String SUPER_CLASS_URI = URI_PREFIX + SUPER_CLASS_LABEL;
    protected static final String OTHER_CLASS_LABEL = "other_" + CLASS_LABEL;
    protected static final String OTHER_CLASS_URI = URI_PREFIX + OTHER_CLASS_LABEL;

    //attributes
    protected static final String ATTRIBUTE_URI = CLASS_URI + "." + ATTRIBUTE_LABEL;
    protected static final String EXISTING_ATTRIBUTE_LABEL = "existing_" + ATTRIBUTE_LABEL;
    protected static final String EXISTING_ATTRIBUTE_URI = URI_PREFIX + CLASS_LABEL + "." + EXISTING_ATTRIBUTE_LABEL;
    protected static final String OTHER_ATTRIBUTE_LABEL = "other_" + ATTRIBUTE_LABEL;
    protected static final String OTHER_ATTRIBUTE_URI = OTHER_CLASS_URI + "." + OTHER_ATTRIBUTE_LABEL;

    //packages
    protected static final String PACKAGE_URI = URI_PREFIX + "Package_" + PACKAGE_LABEL;
    protected static final String OTHER_PACKAGE_URI = OTHER_URI_PREFIX + "Package_" + OTHER_PACKAGE_LABEL;

    //associations
    protected static final UUID ASSOC_UUID = UUID.fromString("43236908-a7f7-4749-bb8b-3ac9250de656");
    protected static final UUID INVERSE_ASSOC_UUID = UUID.fromString("43836908-b7f7-4749-bb8b-3ac9250de657");
    protected static final String EXISTING_ASSOC_URI = CLASS_URI + "." + OTHER_CLASS_LABEL;
    protected static final String EXISTING_INVERSE_ASSOC_URI = OTHER_CLASS_URI + "." + CLASS_LABEL;
    protected static final String INVERSE_LABEL = CLASS_LABEL + "Inverse";
    protected static final String INVERSE_URI = URI_PREFIX + INVERSE_LABEL;
    protected static final String ASSOC_URI = CLASS_URI + "." + INVERSE_LABEL;
    protected static final String INVERSE_ASSOC_URI = INVERSE_URI + "." + CLASS_LABEL;

    //enum entries
    protected static final String ENUM_ENTRY_URI = CLASS_URI + "." + ENUM_ENTRY_LABEL;
    protected static final String EXISTING_ENUM_ENTRY_LABEL = "existing_" + ENUM_ENTRY_LABEL;
    protected static final String EXISTING_ENUM_ENTRY_URI = EXISTING_CLASS_URI + "." + EXISTING_ENUM_ENTRY_LABEL;

    @BeforeAll
    static void setUpEnvironment() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl());
        addGraphFromFile(BASE_FILENAME);
    }

    protected static void addGraphFromFile(String fileName) {
        byte[] content;
        try {
            content = Files.readAllBytes(Path.of(PATH + fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var file = new MockMultipartFile(fileName, fileName, "text/turtle", content);
        var graph = new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.getGraphUri())
                        .build()
                        .graph();
        databasePort.createGraph(graphIdentifier, graph);
        testGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
    }

    protected void addTriple(Node subject, Node predicate, Node object) {
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        try {
            graph.begin(TxnType.WRITE);
            graph.add(subject, predicate, object);
            graph.commit();
        } finally {
            graph.end();
        }
    }

    /**
     * Use this method to execute write actions per Update class by bulding the UpdateBuilder
     */
    protected void executeUpdateOnTestGraph(Update update) {
        InMemorySparqlExecutioner.executeSingleUpdate(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), update, graphIdentifier.getGraphUri());
    }

    /**
     * Use this method to execute write actions in a transaction using lambda expression
     */
    protected void executeWriteTransaction(Consumer<Graph> graphOperation) {
        try {
            testGraph.begin(TxnType.WRITE);
            graphOperation.accept(testGraph);
            testGraph.commit();
        } finally {
            if (testGraph != null) {
                testGraph.end();
            }
        }
    }
}
