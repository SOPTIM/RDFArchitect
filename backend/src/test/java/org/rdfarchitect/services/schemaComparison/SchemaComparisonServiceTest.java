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

package org.rdfarchitect.services.schemaComparison;

import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.SchemaComparisonService;
import org.rdfarchitect.models.changes.triplechanges.TripleClassChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePropertyChange;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.services.compare.SchemaComparisonService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static utils.TestUtils.*;

class SchemaComparisonServiceTest {

    private SchemaComparisonService service;
    private DatabasePort databasePort;

    private static final GraphIdentifier GRAPH_IDENTIFIER = new GraphIdentifier("default", "default");
    private static final GraphIdentifier OTHER_GRAPH_IDENTIFIER = new GraphIdentifier("default", "http://example.org/otherGraph");

    private static final String PATH = "src/test/java/org/rdfarchitect/services/schemaComparison/graphs/";

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl());
        service = new SchemaComparisonService(databasePort);

        var file = readMultipartFileFromFile(PATH, "inMemoryGraph.ttl");
        var graphSource = new GraphFileSourceBuilderImpl()
                  .setFile(file)
                  .setGraphName(GRAPH_IDENTIFIER.getGraphUri())
                  .build();
        databasePort.createGraph(GRAPH_IDENTIFIER, graphSource.graph());
    }

    @Test
    void compareSchemas_changedPackageProperties_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedPackageDefinition.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUri()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getLabel()).isEqualTo("package");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
        assertThat(result.getFirst().getChanges()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .contains(new TriplePropertyChange(RDFS.comment.toString(), "\"This is a new comment\"", null));
    }

    @Test
    void compareSchemas_changedPackageLabel_returnComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedPackageLabel.ttl");

        var package1 = new TriplePackageChange();
        package1.setUri("http://example.org#package");
        package1.setLabel("package");
        package1.setChanges(List.of(
                  new TriplePropertyChange(RDF.type.getURI(), null, CIMS.classCategory.toString()),
                  new TriplePropertyChange(RDFS.label.getURI(), null, "\"package\"@en")));

        var class1 = new TripleClassChange();
        class1.setUri("http://example.org#class");
        class1.setLabel("class");
        class1.setChanges(List.of(
                  new TriplePropertyChange(CIMS.belongsToCategory.toString(), "http://example.org#newPackage", "http://example.org#package")));
        package1.setClasses(List.of(class1));

        var package2 = new TriplePackageChange();
        package2.setUri("http://example.org#newPackage");
        package2.setLabel("newPackage");
        package2.setChanges(List.of(
                  new TriplePropertyChange(RDF.type.getURI(), CIMS.classCategory.toString(), null),
                  new TriplePropertyChange(RDFS.label.getURI(), "\"newPackage\"@en", null)));

        var class2 = new TripleClassChange();
        class2.setUri("http://example.org#class");
        class2.setLabel("class");
        class2.setChanges(List.of(
                  new TriplePropertyChange(CIMS.belongsToCategory.toString(), "http://example.org#newPackage", "http://example.org#package")));
        package2.setClasses(List.of(class2));

        var expected = List.of(package1, package2);
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void compareSchemas_newPackageAndClass_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "newPackageAndClass.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUri()).isEqualTo("http://example.org#newPackage");
        assertThat(result.getFirst().getLabel()).isEqualTo("newPackage");
        assertThat(result.getFirst().getChanges()).isEqualTo(
                  List.of(
                            new TriplePropertyChange(RDF.type.toString(), CIMS.classCategory.toString(), null),
                            new TriplePropertyChange(RDFS.label.toString(), "\"newPackage\"@en", null)
                         )
                                                        );

        var expectedClass = new TripleClassChange();
        expectedClass.setUri("http://example.org#newClass");
        expectedClass.setLabel("newClass");
        expectedClass.setChanges(List.of(
                  new TriplePropertyChange(CIMS.belongsToCategory.toString(), "http://example.org#newPackage", null),
                  new TriplePropertyChange(RDF.type.toString(), RDFS.Class.toString(), null),
                  new TriplePropertyChange(RDFS.label.toString(), "\"newClass\"@en", null)));

        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .hasSize(1)
                                              .first()
                                              .isEqualTo(expectedClass);
    }

    @Test
    void compareSchemas_packageDeleted_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "packageDeleted.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getUri()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getLabel()).isEqualTo("package");
        assertThat(result.getFirst().getChanges()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .contains(new TriplePropertyChange(RDFS.label.toString(), null, "\"package\"@en"))
                                              .contains(new TriplePropertyChange(RDF.type.toString(), null, CIMS.classCategory.toString()));

        var expectedClass1 = new TripleClassChange();
        expectedClass1.setUri("http://example.org#class");
        expectedClass1.setLabel("class");
        expectedClass1.setChanges(List.of(
                  new TriplePropertyChange(CIMS.belongsToCategory.toString(), null, "http://example.org#package")));

        assertThat(result.get(0).getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEqualTo(
                  List.of(expectedClass1));

        assertThat(result.get(1).getLabel()).isEqualTo("default");
        assertThat(result.get(1).getChanges()).isNull();

        var expectedClass2 = new TripleClassChange();
        expectedClass2.setUri("http://example.org#class");
        expectedClass2.setLabel("class");
        expectedClass2.setChanges(List.of(
                  new TriplePropertyChange(CIMS.belongsToCategory.toString(), null, "http://example.org#package")));

        assertThat(result.get(1).getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEqualTo(
                  List.of(expectedClass2));
    }

    @Test
    void compareSchemas_changeClassLabel_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedClassLabel.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("default");

        var class1 = new TripleClassChange();
        class1.setUri("http://example.org#subClass");
        class1.setLabel("subClass");
        class1.setChanges(List.of(
                  new TriplePropertyChange(RDFS.subClassOf.toString(), null, "http://example.org#class"),
                  new TriplePropertyChange(RDF.type.toString(), null, RDFS.Class.toString()),
                  new TriplePropertyChange(RDFS.label.toString(), null, "\"subClass\"@en")));

        var class2 = new TripleClassChange();
        class2.setUri("http://example.org#newSubClass");
        class2.setLabel("newSubClass");
        class2.setChanges(List.of(
                  new TriplePropertyChange(RDFS.subClassOf.toString(), "http://example.org#class", null),
                  new TriplePropertyChange(RDF.type.toString(), RDFS.Class.toString(), null),
                  new TriplePropertyChange(RDFS.label.toString(), "\"newSubClass\"@en", null)));

        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .hasSize(2)
                                              .isEqualTo(List.of(class1, class2));
    }

    @Test
    void compareSchemas_changedAttribute_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedAttribute.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUri()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getLabel()).isEqualTo("package");

        var expectedClass = new TripleClassChange();
        expectedClass.setUri("http://example.org#class");
        expectedClass.setLabel("class");

        var attributeChange = new TripleResourceChange();
        attributeChange.setUri("http://example.org#class.attribute");
        attributeChange.setLabel("attribute");
        attributeChange.setChanges(List.of(
                  new TriplePropertyChange(RDFS.range.toString(),
                                           "http://www.w3.org/2001/XMLSchema#int",
                                           "http://www.w3.org/2001/XMLSchema#string")));

        expectedClass.setAttributes(List.of(attributeChange));

        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .hasSize(1)
                                              .first()
                                              .isEqualTo(expectedClass);
    }

    @Test
    void compareSchemas_changedAssociation_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedAssociation.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUri()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getLabel()).isEqualTo("package");

        var expectedClass = new TripleClassChange();
        expectedClass.setUri("http://example.org#class");
        expectedClass.setLabel("class");

        var associationChange = new TripleResourceChange();
        associationChange.setUri("http://example.org#class.associatedClass");
        associationChange.setLabel("class.associatedClass");
        associationChange.setChanges(List.of(
                  new TriplePropertyChange(CIMS.multiplicity.toString(),
                                           "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:0..1",
                                           "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:0..n")));

        expectedClass.setAssociations(List.of(associationChange));

        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .hasSize(1)
                                              .first()
                                              .isEqualTo(expectedClass);
    }

    @Test
    void compareSchemas_changedEnumEntry_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedEnumEntry.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("default");

        var expectedClass = new TripleClassChange();
        expectedClass.setUri("http://example.org#enum");
        expectedClass.setLabel("enum");

        var enumEntryChange = new TripleResourceChange();
        enumEntryChange.setUri("http://example.org#enum.enumEntry");
        enumEntryChange.setLabel("enumEntry");
        enumEntryChange.setChanges(List.of(
                  new TriplePropertyChange(CIMS.stereotype.toString(), null, "\"enum\"")));

        expectedClass.setEnumEntries(List.of(enumEntryChange));

        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                              .hasSize(1)
                                              .first()
                                              .isEqualTo(expectedClass);
    }

    @Test
    void compareSchemas_noChanges_returnsEmptyList() {
        // arrange
        MultipartFile identicalFile = readMultipartFileFromFile(PATH, "inMemoryGraph.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, identicalFile);

        // assert
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void compareSchemas_changedTripleOrder_returnsEmptyList() {
        // arrange
        MultipartFile identicalFile = readMultipartFileFromFile(PATH, "changedOrder.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, identicalFile);

        // assert
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void compareSchemas_dbDb_sameIdentifier_returnsEmptyList() {
        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, GRAPH_IDENTIFIER);

        // assert
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void compareSchemas_dbDb_noChanges_returnsEmptyList() {
        // arrange
        var otherGraphFile = readMultipartFileFromFile(PATH, "inMemoryGraph.ttl");
        var graphSource = new GraphFileSourceBuilderImpl()
                  .setFile(otherGraphFile)
                  .setGraphName(OTHER_GRAPH_IDENTIFIER.getGraphUri())
                  .build();
        databasePort.createGraph(OTHER_GRAPH_IDENTIFIER, graphSource.graph());

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, OTHER_GRAPH_IDENTIFIER);

        // assert
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void compareSchemas_fileFile_noChanges_returnsEmptyList() {
        // arrange
        var file1 = readMultipartFileFromFile(PATH, "inMemoryGraph.ttl");
        var file2 = readMultipartFileFromFile(PATH, "inMemoryGraph.ttl");

        // act
        var result = service.compareSchemas(file1, file2);

        // assert
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
    }
}
