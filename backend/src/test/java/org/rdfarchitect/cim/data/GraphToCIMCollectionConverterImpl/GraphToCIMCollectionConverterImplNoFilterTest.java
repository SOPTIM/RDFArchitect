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

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.query.TxnType;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabase;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class GraphToCIMCollectionConverterImplNoFilterTest {

    private final InMemoryDatabase database = new InMemoryDatabaseImpl();

    private final GraphToCIMCollectionConverterUseCase converter =
            new GraphToCIMCollectionConverterService(new InMemoryDatabaseAdapter(database));

    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");

    private static final String PATH =
            "src/test/java/org/rdfarchitect/cim/data/GraphToCIMCollectionConverterImpl/";

    private final Map<String, String> prefixMapping =
            Map.of(
                    "cim", "http://iec.ch/TC57/2013/CIM-schema-cim16#",
                    "cims", "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#",
                    "example", "http://example.com#",
                    "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                    "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    "xsd", "http://www.w3.org/2001/XMLSchema#");

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
    void convert_onlyPackage_collectionWithOnlyOnePackage() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "package.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getClasses()).isEmpty();

        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getPackages().iterator().next().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "Package_Infrastruktur"));
    }

    @Test
    void convert_singleNoAttributeClass_collectionWithOnlyOneClass() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();

        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getPackages().iterator().next().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "Package_Infrastruktur"));

        assertThat(cimCollection.getClasses()).hasSize(1);

        var cimClass = cimCollection.getClasses().iterator().next();
        assertThat(cimClass.getUri()).isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass"));
        assertThat(cimClass.getLabel()).isEqualTo(new RDFSLabel("ChildClass", "en"));
        assertThat(cimClass.getSuperClass()).isNull();
        assertThat(cimClass.getStereotypes())
                .containsExactly(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#concrete"));
    }

    @Test
    void convert_noClassInSelectedPackage_collectionWithOnlyPackage() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        addFileGraphToDatabase(PATH + "superClass.ttl");
        addFileGraphToDatabase(PATH + "enumClass.ttl");
        var filter = new GraphFilter(true);

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getClasses()).isEmpty();

        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getPackages().iterator().next().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "Package_Infrastruktur"));
    }

    @Test
    void convert_childClassWithSuperClass_collectionWithBothClassesSortedByUri()
            throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        addFileGraphToDatabase(PATH + "superClass.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).hasSize(1);
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);

        assertThat(cimCollection.getClasses()).hasSize(2);

        var classesIterator = cimCollection.getClasses().iterator();
        var childClass = classesIterator.next();
        var superClass = classesIterator.next();

        assertThat(childClass.getSuperClass().getUri()).isEqualTo(superClass.getUri());
        assertThat(superClass.getSuperClass()).isNull();
    }

    @Test
    void convert_childClassWithAttributes_collectionWithClassAndAttributes() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "childClass.ttl");
        addFileGraphToDatabase(PATH + "childClassAttributes.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).hasSize(1);
        assertThat(cimCollection.getAttributes()).hasSize(2);

        var attributesIterator = cimCollection.getAttributes().iterator();
        var attribute1 = attributesIterator.next();
        assertThat(attribute1.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass.attribute1"));
        assertThat(attribute1.getLabel()).isEqualTo(new RDFSLabel("attribute1", "en"));
        assertThat(attribute1.getDomain().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass"));
        assertThat(attribute1.getDataType().getUri())
                .isEqualTo(new URI(prefixMapping.get("xsd") + "string"));
        assertThat(attribute1.getMultiplicity().getUri())
                .isEqualTo(new URI(prefixMapping.get("cims") + "M:1..1"));
        assertThat(attribute1.getStereotype())
                .isEqualTo(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#attribute"));

        var attribute2 = attributesIterator.next();
        assertThat(attribute2.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass.attribute2"));
        assertThat(attribute2.getLabel()).isEqualTo(new RDFSLabel("attribute2", "en"));
        assertThat(attribute2.getDomain().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass"));
        assertThat(attribute2.getDataType().getUri())
                .isEqualTo(new URI(prefixMapping.get("xsd") + "integer"));
        assertThat(attribute2.getMultiplicity().getUri())
                .isEqualTo(new URI(prefixMapping.get("cims") + "M:1..1"));
        assertThat(attribute2.getStereotype())
                .isEqualTo(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#attribute"));
    }

    @Test
    void convert_enumClassWithEnumEntries_collectionWithEnumClassAndEntries() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "enumClass.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getAssociations()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);
        assertThat(cimCollection.getClasses()).isEmpty();

        assertThat(cimCollection.getEnums()).hasSize(1);
        var enumClass = cimCollection.getEnums().iterator().next();
        assertThat(enumClass.getUri()).isEqualTo(new URI(prefixMapping.get("cim") + "EnumClass"));
        assertThat(enumClass.getLabel()).isEqualTo(new RDFSLabel("EnumClass", "en"));
        assertThat(enumClass.getSuperClass()).isNull();
        assertThat(enumClass.getStereotypes())
                .containsExactly(
                        new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#enumeration"));
        assertThat(enumClass.getBelongsToCategory().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "Package_Infrastruktur"));
        assertThat(enumClass.getComment().getValue()).isEqualTo("enumClassComment");
        assertThat(enumClass.getComment().getFormat())
                .isEqualTo(new URI(prefixMapping.get("rdf") + "XMLLiteral"));

        assertThat(cimCollection.getEnumEntries()).hasSize(2);
        var enumEntriesIterator = cimCollection.getEnumEntries().iterator();
        var enumEntry1 = enumEntriesIterator.next();
        assertThat(enumEntry1.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "EnumClass.ENUMENTRY1"));
        assertThat(enumEntry1.getType().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "EnumClass"));
        assertThat(enumEntry1.getLabel()).isEqualTo(new RDFSLabel("ENUMENTRY1", "en"));

        var enumEntry2 = enumEntriesIterator.next();
        assertThat(enumEntry2.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "EnumClass.ENUMENTRY2"));
        assertThat(enumEntry2.getType().getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "EnumClass"));
        assertThat(enumEntry2.getLabel()).isEqualTo(new RDFSLabel("ENUMENTRY2", "en"));
    }

    @Test
    void convert_associatedClasses_collectionWithClassesAndAssociation() throws IOException {
        // Arrange
        addFileGraphToDatabase(PATH + "childClassToAssociatedClassAssociation.ttl");
        var filter = new GraphFilter(true);
        filter.setPackageUUID("123e4567-e89b-12d3-a456-426614174000");

        // Act
        var cimCollection = converter.convert(graphIdentifier, filter);

        // Assert
        assertThat(cimCollection.getAttributes()).isEmpty();
        assertThat(cimCollection.getEnums()).isEmpty();
        assertThat(cimCollection.getEnumEntries()).isEmpty();
        assertThat(cimCollection.getPackages()).hasSize(1);

        assertThat(cimCollection.getClasses()).hasSize(2);
        var classesIterator = cimCollection.getClasses().iterator();
        var class1 = classesIterator.next();
        var class2 = classesIterator.next();

        assertThat(cimCollection.getAssociations()).hasSize(2);
        var associationsIterator = cimCollection.getAssociations().iterator();
        var association1 = associationsIterator.next();
        var association2 = associationsIterator.next();
        assertThat(association1.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "AssociatedClass.ChildClass"));
        assertThat(association1.getLabel()).isEqualTo(new RDFSLabel("ChildClass", "en"));
        assertThat(association1.getDomain().getUri()).isEqualTo(class1.getUri());
        assertThat(association1.getRange().getUri()).isEqualTo(class2.getUri());
        assertThat(association1.getAssociationUsed().getAssociationUsed()).isEqualTo("Yes");
        assertThat(association1.getMultiplicity().getUri())
                .isEqualTo(new URI(prefixMapping.get("cims") + "M:0..n"));
        assertThat(association1.getInverseRoleName().getUri()).isEqualTo(association2.getUri());

        assertThat(association2.getUri())
                .isEqualTo(new URI(prefixMapping.get("cim") + "ChildClass.AssociatedClass"));
        assertThat(association2.getLabel()).isEqualTo(new RDFSLabel("AssociatedClass", "en"));
        assertThat(association2.getDomain().getUri()).isEqualTo(class2.getUri());
        assertThat(association2.getRange().getUri()).isEqualTo(class1.getUri());
        assertThat(association2.getAssociationUsed().getAssociationUsed()).isEqualTo("No");
        assertThat(association2.getMultiplicity().getUri())
                .isEqualTo(new URI(prefixMapping.get("cims") + "M:1"));
        assertThat(association2.getInverseRoleName().getUri()).isEqualTo(association1.getUri());
    }
}
