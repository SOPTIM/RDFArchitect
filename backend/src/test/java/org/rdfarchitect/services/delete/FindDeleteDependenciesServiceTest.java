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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.api.dto.delete.DeleteAction;
import org.rdfarchitect.api.dto.delete.relations.AffectedAssociation;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource.AffectedResourceReason;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContext;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils.CimResourceType;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class FindDeleteDependenciesServiceTest {

    private static final String TEST_DATA_PATH =
            "src/test/java/org/rdfarchitect/services/delete/testdata.ttl";

    private static final GraphIdentifier GRAPH_IDENTIFIER =
            new GraphIdentifier("default", "default");

    // UUIDs from the TTL test data
    private static final UUID PARENT_CLASS_UUID =
            UUID.fromString("05131eaf-a7dd-4ac4-8624-9665990985ab");
    private static final UUID CHILD_CLASS_UUID =
            UUID.fromString("93ee2f31-5ddd-4b25-b119-e90a5ed327b0");
    private static final UUID ASSOCIATED_CLASS_UUID =
            UUID.fromString("f6d92056-c469-40d4-add1-1d6adf2fa7a6");
    private static final UUID PACKAGE_UUID =
            UUID.fromString("0351f9b6-5e91-4059-9d8b-169d28f1b2c8");
    private static final UUID DATATYPE_CLASS_UUID =
            UUID.fromString("db520255-95ef-40d4-b328-e1631e4683a4");
    private static final UUID ONTOLOGY_UUID =
            UUID.fromString("dba8f8e3-bfb3-4e62-9ca5-b0136ed186b2");

    @Mock private DatabasePort databasePort;

    @InjectMocks private FindDeleteDependenciesService service;

    @BeforeEach
    void setUp() throws IOException {
        var graph = GraphFactory.createDefaultGraph();
        InputStream in = Files.newInputStream(Path.of(TEST_DATA_PATH));
        RDFDataMgr.read(graph, in, Lang.TTL);
        in.close();

        var wrappedGraph = new GraphRewindableWithUUIDs(graph, 5, 5);
        var wrappedContext = new GraphWithContext(wrappedGraph);

        when(databasePort.getGraphWithContext(any(GraphIdentifier.class)))
                .thenReturn(wrappedContext);
    }

    // ==================== Simple resource types ====================

    @Test
    void getDeleteDependencies_ontology_returnsAffectedResourceWithDeleteAction() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ONTOLOGY_UUID);

        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(ONTOLOGY_UUID);
        assertThat(result.getType()).isEqualTo(CimResourceType.ONTOLOGY);
        assertThat(result.getReason()).isEqualTo(AffectedResourceReason.DELETION_REQUESTED_BY_USER);
        assertThat(result.getActions()).containsExactly(DeleteAction.DELETE);
    }

    @Test
    void getDeleteDependencies_datatypeClass_returnsClassWithAttributesDependingOnIt() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, DATATYPE_CLASS_UUID);

        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(DATATYPE_CLASS_UUID);
        assertThat(result.getType()).isEqualTo(CimResourceType.CLASS);
        assertThat(result.getActions()).containsExactly(DeleteAction.DELETE);
    }

    // ==================== Class with child classes (inheritance) ====================

    @Test
    void getDeleteDependencies_classWithDirectChild_returnsChildAsAffectedResource() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, PARENT_CLASS_UUID);

        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(PARENT_CLASS_UUID);
        assertThat(result.getType()).isEqualTo(CimResourceType.CLASS);

        var childClasses =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.CLASS)
                        .toList();

        assertThat(childClasses)
                .isNotEmpty()
                .anyMatch(
                        c ->
                                c.getResourceIdentifier().getUuid().equals(CHILD_CLASS_UUID)
                                        && c.getReason() == AffectedResourceReason.CHILD_OF);
    }

    @Test
    void getDeleteDependencies_classWithTransitiveChildren_returnsNestedHierarchy() {
        // AssociatedClass -> ParentClass -> ChildClass (-> AssociatedClass = cycle)
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ASSOCIATED_CLASS_UUID);

        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(ASSOCIATED_CLASS_UUID);

        // ParentClass should be a direct child
        var parentClassChild =
                result.getChildren().stream()
                        .filter(
                                c ->
                                        c.getType() == CimResourceType.CLASS
                                                && c.getResourceIdentifier()
                                                        .getUuid()
                                                        .equals(PARENT_CLASS_UUID))
                        .findFirst();
        assertThat(parentClassChild).isPresent();

        // ChildClass should be nested under ParentClass, not flat
        var childClassNested =
                parentClassChild.get().getChildren().stream()
                        .filter(
                                c ->
                                        c.getType() == CimResourceType.CLASS
                                                && c.getResourceIdentifier()
                                                        .getUuid()
                                                        .equals(CHILD_CLASS_UUID))
                        .findFirst();
        assertThat(childClassNested).isPresent();
    }

    @Test
    void getDeleteDependencies_cyclicInheritance_doesNotCauseInfiniteLoop() {
        // AssociatedClass -> ChildClass -> ParentClass -> AssociatedClass = cycle
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ASSOCIATED_CLASS_UUID);

        assertThat(result).isNotNull();
        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(ASSOCIATED_CLASS_UUID);

        // Each class must appear only once in the tree
        var allChildClasses = flattenChildClasses(result);
        var classUuids =
                allChildClasses.stream().map(c -> c.getResourceIdentifier().getUuid()).toList();
        assertThat(classUuids).doesNotHaveDuplicates();
    }

    @Test
    void getDeleteDependencies_childClassActions_containDeleteKeepAndRemoveSubclassReference() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ASSOCIATED_CLASS_UUID);

        var childClasses =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.CLASS)
                        .toList();

        assertThat(childClasses)
                .isNotEmpty()
                .allSatisfy(
                        child ->
                                assertThat(child.getActions())
                                        .containsExactlyInAnyOrder(
                                                DeleteAction.DELETE,
                                                DeleteAction.KEEP,
                                                DeleteAction.REMOVE_SUBCLASS_REFERENCE));
    }

    // ==================== Class with associations ====================

    @Test
    void getDeleteDependencies_classWithAssociations_returnsAssociationsAsAffectedResources() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ASSOCIATED_CLASS_UUID);

        var associations =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.ASSOCIATION)
                        .toList();

        assertThat(associations)
                .isNotEmpty()
                .allSatisfy(
                        assoc ->
                                assertThat(assoc.getReason())
                                        .isEqualTo(
                                                AffectedResourceReason
                                                        .REFENCES_DELETED_CLASS_VIA_ASSOCIATION));
    }

    @Test
    void getDeleteDependencies_associationWithTarget_returnsAffectedAssociationWithTarget() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, ASSOCIATED_CLASS_UUID);

        var affectedAssociations =
                result.getChildren().stream()
                        .filter(AffectedAssociation.class::isInstance)
                        .map(c -> (AffectedAssociation) c)
                        .toList();

        assertThat(affectedAssociations)
                .isNotEmpty()
                .allSatisfy(assoc -> assertThat(assoc.getTarget()).isNotNull());
    }

    // ==================== Child classes have their own dependencies ====================

    @Test
    void getDeleteDependencies_childClassWithAssociations_childHasAssociationsAsChildren() {
        // When ParentClass is deleted, ChildClass should appear as a child
        // and ChildClass should have its own associations as children
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, PARENT_CLASS_UUID);

        var childClassAffected =
                result.getChildren().stream()
                        .filter(
                                c ->
                                        c.getType() == CimResourceType.CLASS
                                                && c.getResourceIdentifier()
                                                        .getUuid()
                                                        .equals(CHILD_CLASS_UUID))
                        .findFirst();
        assertThat(childClassAffected).isPresent();

        var childAssociations =
                childClassAffected.get().getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.ASSOCIATION)
                        .toList();
        assertThat(childAssociations).isNotEmpty();
    }

    // ==================== Class used as datatype ====================

    @Test
    void getDeleteDependencies_classUsedAsDatatype_returnsAttributeAsAffectedResource() {
        // DatatypeClass is used as datatype in ParentClass.attr1
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, DATATYPE_CLASS_UUID);

        var attributes =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.ATTRIBUTE)
                        .toList();

        assertThat(attributes)
                .isNotEmpty()
                .allSatisfy(
                        attr ->
                                assertThat(attr.getReason())
                                        .isEqualTo(
                                                AffectedResourceReason
                                                        .USES_DELETED_CLASS_AS_DATATYPE));
    }

    // ==================== Delete package ====================

    @Test
    void getDeleteDependencies_package_returnsAllClassesInPackageAsChildren() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, PACKAGE_UUID);

        assertThat(result.getResourceIdentifier().getUuid()).isEqualTo(PACKAGE_UUID);
        assertThat(result.getType()).isEqualTo(CimResourceType.PACKAGE);
        assertThat(result.getReason()).isEqualTo(AffectedResourceReason.DELETION_REQUESTED_BY_USER);

        var childClasses =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.CLASS)
                        .toList();

        // Package_Package contains: ParentClass, ChildClass, AssociatedClass
        assertThat(childClasses).hasSize(3);

        var childUuids =
                childClasses.stream().map(c -> c.getResourceIdentifier().getUuid()).toList();
        assertThat(childUuids)
                .containsExactlyInAnyOrder(
                        PARENT_CLASS_UUID, CHILD_CLASS_UUID, ASSOCIATED_CLASS_UUID);
    }

    @Test
    void getDeleteDependencies_package_classesHaveCorrectReasonAndActions() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, PACKAGE_UUID);

        var childClasses =
                result.getChildren().stream()
                        .filter(c -> c.getType() == CimResourceType.CLASS)
                        .toList();

        assertThat(childClasses)
                .isNotEmpty()
                .allSatisfy(
                        cls -> {
                            assertThat(cls.getReason())
                                    .isEqualTo(AffectedResourceReason.CONTAINED_IN_PACKAGE);
                            assertThat(cls.getActions())
                                    .containsExactlyInAnyOrder(
                                            DeleteAction.DELETE,
                                            DeleteAction.KEEP,
                                            DeleteAction.REMOVE_PACKAGE_REFERENCE);
                        });
    }

    @Test
    void getDeleteDependencies_package_classesInPackageHaveTheirOwnDependencies() {
        var result = service.getDeleteDependencies(GRAPH_IDENTIFIER, PACKAGE_UUID);

        var associatedClassAffected =
                result.getChildren().stream()
                        .filter(
                                c ->
                                        c.getResourceIdentifier()
                                                .getUuid()
                                                .equals(ASSOCIATED_CLASS_UUID))
                        .findFirst();
        assertThat(associatedClassAffected).isPresent();

        // AssociatedClass has associations and child classes
        assertThat(associatedClassAffected.get().getChildren()).isNotEmpty();
    }

    // ==================== Helper methods ====================

    private List<AffectedResource> flattenChildClasses(AffectedResource root) {
        var result = new ArrayList<AffectedResource>();
        if (root.getChildren() == null) {
            return result;
        }
        for (var child : root.getChildren()) {
            if (child.getType() == CimResourceType.CLASS) {
                result.add(child);
                result.addAll(flattenChildClasses(child));
            }
        }
        return result;
    }
}
