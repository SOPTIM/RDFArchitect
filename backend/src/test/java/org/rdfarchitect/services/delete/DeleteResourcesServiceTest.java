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

package org.rdfarchitect.services.delete;

import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.api.dto.delete.DeleteAction;
import org.rdfarchitect.api.dto.delete.ResourceDeleteRequest;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContext;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteResourcesServiceTest {

    private static final String TEST_DATA_PATH = "src/test/java/org/rdfarchitect/services/delete/testdata.ttl";

    private static final GraphIdentifier GRAPH_IDENTIFIER = new GraphIdentifier("default", "default");

    private static final String CIM_NS = "http://iec.ch/TC57/CIM100#";

    // UUIDs from the TTL test data
    private static final UUID PARENT_CLASS_UUID = UUID.fromString("05131eaf-a7dd-4ac4-8624-9665990985ab");
    private static final UUID CHILD_CLASS_UUID = UUID.fromString("93ee2f31-5ddd-4b25-b119-e90a5ed327b0");
    private static final UUID ASSOCIATED_CLASS_UUID = UUID.fromString("f6d92056-c469-40d4-add1-1d6adf2fa7a6");
    private static final UUID PACKAGE_UUID = UUID.fromString("0351f9b6-5e91-4059-9d8b-169d28f1b2c8");
    private static final UUID DATATYPE_CLASS_UUID = UUID.fromString("db520255-95ef-40d4-b328-e1631e4683a4");
    private static final UUID ONTOLOGY_UUID = UUID.fromString("dba8f8e3-bfb3-4e62-9ca5-b0136ed186b2");
    private static final UUID ATTR1_UUID = UUID.fromString("dc934c7b-6c4d-4177-9832-bcb955f25414");
    private static final UUID ASSOC_NONEXISTING_UUID = UUID.fromString("b2a306f1-4789-4a12-b045-04a014e7a937");
    private static final UUID ASSOC_TEMP_ASSOCIATED_UUID = UUID.fromString("c3b417f2-5890-5b23-c156-15b015e8b048");
    private static final UUID ASSOC_ASSOCIATED_CHILD_UUID = UUID.fromString("abfab117-e4bc-4814-9b5a-3aed72de8a2d");
    private static final UUID ASSOC_CHILD_ASSOCIATED_UUID = UUID.fromString("bc0bc228-f5cd-5925-ab6b-4bfe83ef9b3e");

    @Mock
    private DatabasePort databasePort;

    @InjectMocks
    private DeleteResourcesService service;

    private GraphRewindableWithUUIDs wrappedGraph;

    @BeforeEach
    void setUp() throws IOException {
        var graph = GraphFactory.createDefaultGraph();
        InputStream in = Files.newInputStream(Path.of(TEST_DATA_PATH));
        RDFDataMgr.read(graph, in, Lang.TTL);
        in.close();

        wrappedGraph = new GraphRewindableWithUUIDs(graph, 5, 5);
        var wrappedContext = new GraphWithContext(wrappedGraph);

        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(wrappedContext);
    }

    private Model readModel() {
        wrappedGraph.begin(TxnType.READ);
        return ModelFactory.createModelForGraph(wrappedGraph);
    }

    private void endRead() {
        wrappedGraph.end();
    }

    private ResourceDeleteRequest request(UUID uuid, DeleteAction action) {
        var req = new ResourceDeleteRequest();
        req.setUuid(uuid);
        req.setAction(action);
        return req;
    }

    /**
     * Asserts that the resource has at most its UUID triple left (all other properties removed).
     */
    private void assertResourceRemovedOrOnlyUuid(String localName) {
        var model = readModel();
        try {
            var resource = ResourceFactory.createResource(CIM_NS + localName);
            var nonUuidStatements = model.listStatements(resource, null, (RDFNode) null)
                                         .filterDrop(stmt -> stmt.getPredicate().equals(RDFA.uuid))
                                         .toList();
            assertThat(nonUuidStatements).isEmpty();
        } finally {
            endRead();
        }
    }

    /**
     * Asserts that the resource still has properties beyond just UUID.
     */
    private void assertResourceFullyPresent(String localName) {
        var model = readModel();
        try {
            var resource = ResourceFactory.createResource(CIM_NS + localName);
            var nonUuidStatements = model.listStatements(resource, null, (RDFNode) null)
                                         .filterDrop(stmt -> stmt.getPredicate().equals(RDFA.uuid))
                                         .toList();
            assertThat(nonUuidStatements).isNotEmpty();
        } finally {
            endRead();
        }
    }

    // ==================== Delete ontology ====================

    @Test
    void executeDeleteRequests_deleteOntology_removesOntology() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ONTOLOGY_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("Ontology");
    }

    // ==================== Delete package ====================

    @Test
    void executeDeleteRequests_deletePackage_removesPackagePropertiesButPreservesUuid() {
        // Package is still referenced by classes via belongsToCategory, so UUID is preserved
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PACKAGE_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("Package_Package");
    }

    @Test
    void executeDeleteRequests_keepPackage_doesNotRemovePackage() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PACKAGE_UUID, DeleteAction.KEEP)));

        assertResourceFullyPresent("Package_Package");
    }

    // ==================== Delete class ====================

    @Test
    void executeDeleteRequests_deleteClass_removesClassAndOwnedAttributes() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PARENT_CLASS_UUID, DeleteAction.DELETE)));

        // ParentClass is referenced via subClassOf, so UUID may be preserved
        assertResourceRemovedOrOnlyUuid("ParentClass");
        // ParentClass.attr1 is owned by ParentClass and should be removed
        assertResourceRemovedOrOnlyUuid("ParentClass.attr1");
    }

    @Test
    void executeDeleteRequests_deleteClass_removesClassProperties() {
        // ChildClass is referenced by AssociatedClass via subClassOf
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(CHILD_CLASS_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("ChildClass");
    }

    @Test
    void executeDeleteRequests_keepClass_doesNotRemoveClass() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PARENT_CLASS_UUID, DeleteAction.KEEP)));

        assertResourceFullyPresent("ParentClass");
    }

    @Test
    void executeDeleteRequests_removeSubclassReference_removesOnlySubClassOfTriple() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(CHILD_CLASS_UUID, DeleteAction.REMOVE_SUBCLASS_REFERENCE)));

        var model = readModel();
        try {
            var childClassResource = ResourceFactory.createResource(CIM_NS + "ChildClass");
            assertThat(model.listStatements(childClassResource, RDFS.subClassOf, (RDFNode) null).hasNext()).isFalse();
            assertThat(childClassResource.inModel(model).hasProperty(RDF.type, RDFS.Class)).isTrue();
            assertThat(childClassResource.inModel(model).hasProperty(RDFS.label)).isTrue();
        } finally {
            endRead();
        }
    }

    @Test
    void executeDeleteRequests_removePackageReference_removesOnlyBelongsToCategoryTriple() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(CHILD_CLASS_UUID, DeleteAction.REMOVE_PACKAGE_REFERENCE)));

        var model = readModel();
        try {
            var childClassResource = ResourceFactory.createResource(CIM_NS + "ChildClass");
            assertThat(model.listStatements(childClassResource, CIMS.belongsToCategory, (RDFNode) null).hasNext()).isFalse();
            assertThat(childClassResource.inModel(model).hasProperty(RDF.type, RDFS.Class)).isTrue();
            assertThat(childClassResource.inModel(model).hasProperty(RDFS.label)).isTrue();
        } finally {
            endRead();
        }
    }

    // ==================== Delete attribute ====================

    @Test
    void executeDeleteRequests_deleteAttribute_removesAttribute() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ATTR1_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("ParentClass.attr1");
    }

    @Test
    void executeDeleteRequests_keepAttribute_doesNotRemoveAttribute() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ATTR1_UUID, DeleteAction.KEEP)));

        assertResourceFullyPresent("ParentClass.attr1");
    }

    // ==================== Delete association ====================

    @Test
    void executeDeleteRequests_deleteAssociation_removesAssociationAndInverse() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ASSOC_NONEXISTING_UUID, DeleteAction.DELETE)));

        // Both the association and its inverse should have their properties removed
        assertResourceRemovedOrOnlyUuid("AssociatedClass.NonExisting");
        assertResourceRemovedOrOnlyUuid("Temp.AssociatedClass");
    }

    @Test
    void executeDeleteRequests_keepAssociation_doesNotRemoveAssociation() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ASSOC_NONEXISTING_UUID, DeleteAction.KEEP)));

        assertResourceFullyPresent("AssociatedClass.NonExisting");
    }

    @Test
    void executeDeleteRequests_deleteAssociationOtherDirection_removesAssociationAndInverse() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ASSOC_ASSOCIATED_CHILD_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("AssociatedClass.ChildClass");
        assertResourceRemovedOrOnlyUuid("ChildClass.AssociatedClass");
    }

    // ==================== Delete class with external associations ====================

    @Test
    void executeDeleteRequests_deleteClassWithExternalAssociation_removesExternalAssociations() {
        // AssociatedClass has an association to Temp (external, not in any package)
        // Deleting AssociatedClass should remove associations referencing external resources
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ASSOCIATED_CLASS_UUID, DeleteAction.DELETE)));

        assertResourceRemovedOrOnlyUuid("AssociatedClass");
        assertResourceRemovedOrOnlyUuid("AssociatedClass.NonExisting");
    }

    @Test
    void executeDeleteRequests_deleteClass_doesNotRemoveInternalAssociations() {
        // AssociatedClass.ChildClass points to ChildClass (internal, in same package)
        // Deleting AssociatedClass should NOT remove internal associations
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(ASSOCIATED_CLASS_UUID, DeleteAction.DELETE)));

        assertResourceFullyPresent("AssociatedClass.ChildClass");
    }

    // ==================== Multiple delete requests ====================

    @Test
    void executeDeleteRequests_multipleRequests_executesAllInOrder() {
        var requests = List.of(
                  request(ATTR1_UUID, DeleteAction.DELETE),
                  request(CHILD_CLASS_UUID, DeleteAction.REMOVE_SUBCLASS_REFERENCE),
                  request(ONTOLOGY_UUID, DeleteAction.DELETE)
                              );

        service.executeDeleteRequests(GRAPH_IDENTIFIER, requests);

        // Attribute should be removed
        assertResourceRemovedOrOnlyUuid("ParentClass.attr1");

        // ChildClass should still exist but without subClassOf
        var model = readModel();
        try {
            var childClassResource = ResourceFactory.createResource(CIM_NS + "ChildClass");
            assertThat(childClassResource.inModel(model).hasProperty(RDF.type, RDFS.Class)).isTrue();
            assertThat(model.listStatements(childClassResource, RDFS.subClassOf, (RDFNode) null).hasNext()).isFalse();
        } finally {
            endRead();
        }

        // Ontology should be removed
        assertResourceRemovedOrOnlyUuid("Ontology");
    }

    // ==================== Invalid actions are skipped gracefully ====================

    @Test
    void executeDeleteRequests_unsupportedActionForPackage_skipsWithoutException() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PACKAGE_UUID, DeleteAction.REMOVE_SUBCLASS_REFERENCE)));

        assertResourceFullyPresent("Package_Package");
    }

    @Test
    void executeDeleteRequests_nullAction_skipsWithoutException() {
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(PARENT_CLASS_UUID, null)));

        assertResourceFullyPresent("ParentClass");
    }

    // ==================== Referenced resources preserve UUID ====================

    @Test
    void executeDeleteRequests_deleteReferencedResource_preservesUuidTriple() {
        // DatatypeClass is referenced by ParentClass.attr1 via cims:dataType
        service.executeDeleteRequests(GRAPH_IDENTIFIER, List.of(request(DATATYPE_CLASS_UUID, DeleteAction.DELETE)));

        var model = readModel();
        try {
            var datatypeResource = ResourceFactory.createResource(CIM_NS + "DatatypeClass");

            // UUID triple should be preserved since it's referenced elsewhere
            var uuidStatements = model.listStatements(datatypeResource, RDFA.uuid, (RDFNode) null).toList();
            assertThat(uuidStatements).isNotEmpty();

            // All other properties should be removed
            var otherStatements = model.listStatements(datatypeResource, null, (RDFNode) null)
                                       .filterDrop(stmt -> stmt.getPredicate().equals(RDFA.uuid))
                                       .toList();
            assertThat(otherStatements).isEmpty();
        } finally {
            endRead();
        }
    }
}
