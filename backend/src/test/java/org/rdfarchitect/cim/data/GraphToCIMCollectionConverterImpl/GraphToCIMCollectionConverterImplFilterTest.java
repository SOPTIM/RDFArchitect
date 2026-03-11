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

package org.rdfarchitect.cim.data.GraphToCIMCollectionConverterImpl;

import org.apache.jena.query.TxnType;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabase;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterUseCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class GraphToCIMCollectionConverterImplFilterTest {

    private final InMemoryDatabase database = new InMemoryDatabaseImpl();

    private final GraphToCIMCollectionConverterUseCase converter = new GraphToCIMCollectionConverterService(new InMemoryDatabaseAdapter(database));

    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    private static final String PATH = "src/test/java/org/rdfarchitect/cim/data/GraphToCIMCollectionConverterImpl/";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
    }

    private void addFileGraphToDatabase(String fileName) throws IOException {
        if (!database.containsGraph(graphIdentifier)) {
            database.create(graphIdentifier, GraphFactory.createDefaultGraph());
        }
        GraphRewindableWithUUIDs graphRewindable = null;
        try {
            var graph = GraphFactory.createDefaultGraph();
            InputStream in = Files.newInputStream(Path.of(fileName));
            RDFDataMgr.read(graph, in, Lang.TTL);
            graphRewindable = database.begin(graphIdentifier, TxnType.WRITE);
            for (var triple : graph.find().toList()) {
                graphRewindable.add(triple);
            }
            graphRewindable.commit();
        } finally {
            if (graphRewindable != null) {
                graphRewindable.end();
            }
        }
    }

    @Test
    void convert_associatedClassesFilterNoAssociations_collectionWithClassesAndNoAssociation() throws IOException {
        //Arrange
        addFileGraphToDatabase(PATH + "childClassToAssociatedClassAssociation.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");
        filter.setIncludeAssociations(false);

        //Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        //Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).hasSize(1);
        assertThat(cimCollection.getAssociations()).isEmpty();
    }

    @Test
    void convert_associatedClassesFilterNoRelationsToExternalPackages_collectionWithOnlyClassesInPackage() throws IOException {
        //Arrange
        addFileGraphToDatabase(PATH + "childClassToAssociatedClassAssociation.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");
        filter.setIncludeRelationsToExternalPackages(false);

        //Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        //Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).hasSize(1);
        assertThat(cimCollection.getAssociations()).isEmpty();
    }

    @Test
    void convert_classWithAttributesFilterNoAttributes_collectionWithNoAttributes() throws IOException {
        //Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        addFileGraphToDatabase(PATH + "childClassAttributes.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");
        filter.setIncludeAttributes(false);

        //Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        //Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).hasSize(1);
        assertThat(cimCollection.getAssociations()).isEmpty();
    }

    @Test
    void convert_enumClassFilterNoEnumEntries_collectionWithNoEnumEntries() throws IOException {
        //Arrange
        addFileGraphToDatabase(PATH + "enumClass.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");
        filter.setIncludeEnumEntries(false);

        //Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        //Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).hasSize(1);
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).isEmpty();
        assertThat(cimCollection.getAssociations()).isEmpty();
    }

    @Test
    void convert_childAndSuperClassFilterNoInheritance_collectionWithClassesAndNoInheritance() throws IOException {
        //Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        addFileGraphToDatabase(PATH + "superClass.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");
        filter.setIncludeInheritance(false);
        filter.setIncludeAttributes(false);

        //Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        //Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getClasses()).hasSize(2);
        var classIterator = cimCollection.getClasses().iterator();
        var childClass = classIterator.next();
        var superClass = classIterator.next();

        assertThat(childClass.getSuperClass()).isNull();
        assertThat(superClass.getSuperClass()).isNull();
    }
}