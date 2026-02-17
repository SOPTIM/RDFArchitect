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
import org.rdfarchitect.cim.changes.ClassChange;
import org.rdfarchitect.cim.changes.NestedObjectChange;
import org.rdfarchitect.cim.changes.PackageChange;
import org.rdfarchitect.cim.changes.PropertyChange;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.SchemaComparisonService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.*;
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
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
        assertThat(result.getFirst().getChanges()).asInstanceOf(InstanceOfAssertFactories.MAP)
                                                  .containsEntry(RDFS.comment.toString(), new PropertyChange(null, "\"This is a new comment\""));
    }

    @Test
    void compareSchemas_changedPackageLabel_returnComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedPackageLabel.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        var expected = List.of(
                  PackageChange.builder()
                               .label("http://example.org#package")
                               .changes(Map.of(
                                         RDFS.label.getURI(), PropertyChange.builder().from("\"package\"@en").to(null).build(),
                                         RDF.type.getURI(), PropertyChange.builder().from(CIMS.classCategory.toString()).to(null).build()
                                              ))
                               .classes(List.of(
                                         ClassChange.builder()
                                                    .label("http://example.org#class")
                                                    .changes(Map.of(
                                                              CIMS.belongsToCategory.toString(),
                                                              PropertyChange.builder().from("http://example.org#package").to("http://example.org#newPackage").build()
                                                                   ))
                                                    .build()
                                               ))
                               .build(),
                  PackageChange.builder()
                               .label("http://example.org#newPackage")
                               .changes(Map.of(
                                         RDFS.label.getURI(), PropertyChange.builder().from(null).to("\"newPackage\"@en").build(),
                                         RDF.type.getURI(), PropertyChange.builder().from(null).to(CIMS.classCategory.toString()).build()
                                              ))
                               .classes(List.of(
                                         ClassChange.builder()
                                                    .label("http://example.org#class")
                                                    .changes(Map.of(
                                                              CIMS.belongsToCategory.toString(),
                                                              PropertyChange.builder().from("http://example.org#package").to("http://example.org#newPackage").build()
                                                                   ))
                                                    .build()
                                               ))
                               .build()
                              );

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void compareSchemas_newPackageAndClass_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "newPackageAndClass.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("http://example.org#newPackage");
        assertThat(result.getFirst().getChanges()).isEqualTo(
                  Map.of(
                            RDF.type.toString(), PropertyChange.builder().from(null).to(CIMS.classCategory.toString()).build(),
                            RDFS.label.toString(), PropertyChange.builder().from(null).to("\"newPackage\"@en").build()
                        )
                                                            );
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .hasSize(1)
                                                  .first()
                                                  .isEqualTo(ClassChange.builder()
                                                                        .label("http://example.org#newClass")
                                                                        .changes(Map.of(
                                                                                  CIMS.belongsToCategory.toString(), PropertyChange.builder().from(null)
                                                                                                                                   .to("http://example.org#newPackage").build(),
                                                                                  RDFS.label.toString(), PropertyChange.builder().from(null).to("\"newClass\"@en").build(),
                                                                                  RDF.type.toString(), PropertyChange.builder().from(null).to(RDFS.Class.toString()).build()
                                                                                       ))
                                                                        .build());
    }

    @Test
    void compareSchemas_packageDeleted_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "packageDeleted.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getLabel()).isEqualTo("http://example.org#package");
        assertThat(result.get(0).getChanges()).asInstanceOf(InstanceOfAssertFactories.MAP)
                                              .containsEntry(RDFS.label.toString(), new PropertyChange("\"package\"@en", null))
                                              .containsEntry(RDF.type.toString(), new PropertyChange(CIMS.classCategory.toString(), null));
        assertThat(result.get(0).getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEqualTo(
                  List.of(ClassChange.builder()
                                     .label("http://example.org#class")
                                     .changes(Map.of(
                                               CIMS.belongsToCategory.toString(), PropertyChange.builder().from("http://example.org#package").to(null).build()
                                                    ))
                                     .build())
                                                                                                     );
        assertThat(result.get(1).getLabel()).isEqualTo("default");
        assertThat(result.get(1).getChanges()).isNull();
        assertThat(result.get(1).getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST).isEqualTo(
                  List.of(ClassChange.builder()
                                     .label("http://example.org#class")
                                     .changes(Map.of(
                                               CIMS.belongsToCategory.toString(), PropertyChange.builder().from("http://example.org#package").to(null).build()
                                                    ))
                                     .build()));
    }

    @Test
    void compareSchemas_changeClassLabel_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedClassLabel.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("default");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .hasSize(2)
                                                  .isEqualTo(List.of(ClassChange.builder()
                                                                                .label("http://example.org#subClass")
                                                                                .changes(Map.of(
                                                                                          RDF.type.toString(), PropertyChange.builder().from(RDFS.Class.toString()).to(null)
                                                                                                                             .build(),
                                                                                          RDFS.label.toString(), PropertyChange.builder().from("\"subClass\"@en").to(null).build(),
                                                                                          RDFS.subClassOf.toString(), PropertyChange.builder().from("http://example.org#class")
                                                                                                                                    .to(null).build()
                                                                                               ))
                                                                                .build(),
                                                                     ClassChange.builder()
                                                                                .label("http://example.org#newSubClass")
                                                                                .changes(Map.of(
                                                                                          RDF.type.toString(), PropertyChange.builder().from(null).to(RDFS.Class.toString())
                                                                                                                             .build(),
                                                                                          RDFS.label.toString(), PropertyChange.builder().from(null).to("\"newSubClass\"@en")
                                                                                                                               .build(),
                                                                                          RDFS.subClassOf.toString(), PropertyChange.builder().from(null)
                                                                                                                                    .to("http://example.org#class").build()
                                                                                               ))
                                                                                .build(
                                                                                      )));
    }

    @Test
    void compareSchemas_changedAttribute_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedAttribute.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .hasSize(1)
                                                  .first()
                                                  .isEqualTo(ClassChange.builder()
                                                                        .label("http://example.org#class")
                                                                        .attributes(List.of(NestedObjectChange.builder()
                                                                                                              .label("attribute")
                                                                                                              .changes(Map.of(
                                                                                                                        RDFS.range.toString(), PropertyChange.builder()
                                                                                                                                                             .from("http://www.w3.org/2001/XMLSchema#string")
                                                                                                                                                             .to("http://www.w3.org/2001/XMLSchema#int")
                                                                                                                                                             .build()
                                                                                                                             )).build()
                                                                                           ))
                                                                        .build());
    }

    @Test
    void compareSchemas_changedAssociation_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedAssociation.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("http://example.org#package");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .hasSize(1)
                                                  .first()
                                                  .isEqualTo(ClassChange.builder()
                                                                        .label("http://example.org#class")
                                                                        .associations(List.of(NestedObjectChange.builder()
                                                                                                                .label("class.associatedClass")
                                                                                                                .changes(Map.of(
                                                                                                                          CIMS.multiplicity.toString(), PropertyChange.builder()
                                                                                                                                                                      .from("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:0..n")
                                                                                                                                                                      .to("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:0..1")
                                                                                                                                                                      .build()
                                                                                                                               )).build()
                                                                                             ))
                                                                        .build());
    }

    @Test
    void compareSchemas_changedEnumEntry_returnsComparisonResult() {
        // arrange
        MultipartFile uploadedFile = readMultipartFileFromFile(PATH, "changedEnumEntry.ttl");

        // act
        var result = service.compareSchemas(GRAPH_IDENTIFIER, uploadedFile);

        // assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getLabel()).isEqualTo("default");
        assertThat(result.getFirst().getClasses()).asInstanceOf(InstanceOfAssertFactories.LIST)
                                                  .hasSize(1)
                                                  .first()
                                                  .isEqualTo(ClassChange.builder()
                                                                        .label("http://example.org#enum")
                                                                        .enumEntries(List.of(NestedObjectChange.builder()
                                                                                                               .label("enumEntry")
                                                                                                               .changes(Map.of(
                                                                                                                         CIMS.stereotype.toString(), PropertyChange.builder()
                                                                                                                                                                   .from("\"enum\"")
                                                                                                                                                                   .to(null).build()
                                                                                                                              )).build()
                                                                                            ))
                                                                        .build());
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
