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

package org.rdfarchitect.services.schemamigration.renamings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.services.schemamigration.ChangeObjectTestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RenameObjectBuilderTest {

    @Nested
    class CreateRenameObjectTest {

        @Test
        void createRenameObject_simpleResourceChange_createsRenameWithMergedChanges() {
            var deleted = SemanticResourceChange.builder()
                                                .label("OldName")
                                                .iri("http://example.org#OldName")
                                                .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                .changes(List.of(
                                                          ChangeObjectTestBuilder.fieldChange(SemanticFieldChangeType.COMMENT_CHANGE, "Old comment", null)
                                                                ))
                                                .build();

            var added = SemanticResourceChange.builder()
                                              .label("NewName")
                                              .iri("http://example.org#NewName")
                                              .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                              .changes(List.of(
                                                        ChangeObjectTestBuilder.fieldChange(SemanticFieldChangeType.COMMENT_CHANGE, null, "New comment")
                                                              ))
                                              .build();

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.9);

            var result = RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result.getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.RENAME);
            assertThat(result.getLabel()).isEqualTo("NewName");
            assertThat(result.getIri()).isEqualTo("http://example.org#NewName");
            assertThat(result.getOldIRI()).isEqualTo("http://example.org#OldName");
            assertThat(result.getChanges()).hasSize(1);
        }

        @Test
        void createRenameObject_classChange_mergesAttributesAndAssociations() {
            var attr1 = SemanticAttributeChange.builder()
                                               .label("voltage")
                                               .iri("http://example.org#OldClass.voltage")
                                               .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                               .changes(List.of())
                                               .build();

            var deleted = SemanticClassChange.builder()
                                             .label("OldClass")
                                             .iri("http://example.org#OldClass")
                                             .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                             .changes(List.of())
                                             .attributes(List.of(attr1))
                                             .associations(List.of())
                                             .enumEntries(List.of())
                                             .build();

            var attr2 = SemanticAttributeChange.builder()
                                               .label("voltage")
                                               .iri("http://example.org#NewClass.voltage")
                                               .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                               .changes(List.of())
                                               .build();

            var added = SemanticClassChange.builder()
                                           .label("NewClass")
                                           .iri("http://example.org#NewClass")
                                           .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                           .changes(List.of())
                                           .attributes(List.of(attr2))
                                           .associations(List.of())
                                           .enumEntries(List.of())
                                           .build();

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.85);

            var result = (SemanticClassChange) RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result.getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.RENAME);
            assertThat(result.getLabel()).isEqualTo("NewClass");
            assertThat(result.getOldIRI()).isEqualTo("http://example.org#OldClass");
            assertThat(result.getAttributes()).hasSize(1);
            assertThat(result.getAttributes().getFirst().getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.CHANGE);
        }

        @Test
        void createRenameObject_classWithNewAttribute_includesNewAttribute() {
            var deleted = SemanticClassChange.builder()
                                             .label("OldClass")
                                             .iri("http://example.org#OldClass")
                                             .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                             .changes(List.of())
                                             .attributes(List.of())
                                             .associations(List.of())
                                             .enumEntries(List.of())
                                             .build();

            var newAttr = SemanticAttributeChange.builder()
                                                 .label("newAttribute")
                                                 .iri("http://example.org#NewClass.newAttribute")
                                                 .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                 .changes(List.of())
                                                 .build();

            var added = SemanticClassChange.builder()
                                           .label("NewClass")
                                           .iri("http://example.org#NewClass")
                                           .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                           .changes(List.of())
                                           .attributes(List.of(newAttr))
                                           .associations(List.of())
                                           .enumEntries(List.of())
                                           .build();

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.75);

            var result = (SemanticClassChange) RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result.getAttributes()).hasSize(1);
            assertThat(result.getAttributes().getFirst().getLabel()).isEqualTo("newAttribute");
            assertThat(result.getAttributes().getFirst().getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.ADD);
        }

        @Test
        void createRenameObject_classWithDeletedAttribute_includesDeletedAttribute() {
            var deletedAttr = SemanticAttributeChange.builder()
                                                     .label("oldAttribute")
                                                     .iri("http://example.org#OldClass.oldAttribute")
                                                     .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                     .changes(List.of())
                                                     .build();

            var deleted = SemanticClassChange.builder()
                                             .label("OldClass")
                                             .iri("http://example.org#OldClass")
                                             .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                             .changes(List.of())
                                             .attributes(List.of(deletedAttr))
                                             .associations(List.of())
                                             .enumEntries(List.of())
                                             .build();

            var added = SemanticClassChange.builder()
                                           .label("NewClass")
                                           .iri("http://example.org#NewClass")
                                           .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                           .changes(List.of())
                                           .attributes(List.of())
                                           .associations(List.of())
                                           .enumEntries(List.of())
                                           .build();

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.75);

            var result = (SemanticClassChange) RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result.getAttributes()).hasSize(1);
            assertThat(result.getAttributes().getFirst().getLabel()).isEqualTo("oldAttribute");
            assertThat(result.getAttributes().getFirst().getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.DELETE);
        }
    }

    @Nested
    class MergePropertyListTest {

        @Test
        void mergePropertyList_matchingPropertyByLabel_createsDomainRename() {
            var deletedAttr = SemanticAttributeChange.builder()
                                                     .label("voltage")
                                                     .iri("http://example.org#OldClass.voltage")
                                                     .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                     .changes(List.of())
                                                     .build();

            var addedAttr = SemanticAttributeChange.builder()
                                                   .label("voltage")
                                                   .iri("http://example.org#NewClass.voltage")
                                                   .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                   .changes(List.of())
                                                   .build();

            var domain = SemanticClassChange.builder()
                                            .label("NewClass")
                                            .iri("http://example.org#NewClass")
                                            .oldIRI("http://example.org#OldClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAttr),
                      List.of(deletedAttr),
                      domain
                                                              );

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.CHANGE);
            assertThat(result.getFirst().getOldIRI()).isEqualTo("http://example.org#OldClass.voltage");
        }

        @Test
        void mergePropertyList_noMatchingProperty_keepsBoth() {
            var deletedAttr = SemanticAttributeChange.builder()
                                                     .label("oldProperty")
                                                     .iri("http://example.org#Class.oldProperty")
                                                     .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                     .changes(List.of())
                                                     .build();

            var addedAttr = SemanticAttributeChange.builder()
                                                   .label("newProperty")
                                                   .iri("http://example.org#Class.newProperty")
                                                   .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                   .changes(List.of())
                                                   .build();

            var domain = SemanticClassChange.builder()
                                            .label("TestClass")
                                            .iri("http://example.org#TestClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAttr),
                      List.of(deletedAttr),
                      domain
                                                              );

            assertThat(result).hasSize(2)
                              .anyMatch(attr -> attr.getLabel().equals("oldProperty"))
                              .anyMatch(attr -> attr.getLabel().equals("newProperty"));
        }

        @Test
        void mergePropertyList_multipleProperties_mergesCorrectly() {
            var deletedAttr1 = SemanticAttributeChange.builder()
                                                      .label("voltage")
                                                      .iri("http://example.org#OldClass.voltage")
                                                      .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                      .changes(List.of())
                                                      .build();

            var deletedAttr2 = SemanticAttributeChange.builder()
                                                      .label("current")
                                                      .iri("http://example.org#OldClass.current")
                                                      .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                      .changes(List.of())
                                                      .build();

            var addedAttr1 = SemanticAttributeChange.builder()
                                                    .label("voltage")
                                                    .iri("http://example.org#NewClass.voltage")
                                                    .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                    .changes(List.of())
                                                    .build();

            var addedAttr2 = SemanticAttributeChange.builder()
                                                    .label("power")
                                                    .iri("http://example.org#NewClass.power")
                                                    .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                    .changes(List.of())
                                                    .build();

            var domain = SemanticClassChange.builder()
                                            .label("NewClass")
                                            .iri("http://example.org#NewClass")
                                            .oldIRI("http://example.org#OldClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAttr1, addedAttr2),
                      List.of(deletedAttr1, deletedAttr2),
                      domain
                                                              );

            assertThat(result).hasSize(3)
                              .anyMatch(attr ->
                                                  attr.getLabel().equals("voltage") &&
                                                            attr.getSemanticResourceChangeType() == SemanticResourceChangeType.CHANGE
                                       )
                              .anyMatch(attr ->
                                                  attr.getLabel().equals("current") &&
                                                            attr.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETE
                                       )
                              .anyMatch(attr ->
                                                  attr.getLabel().equals("power") &&
                                                            attr.getSemanticResourceChangeType() == SemanticResourceChangeType.ADD
                                       );
        }

        @Test
        void mergePropertyList_associations_handlesCorrectly() {
            var deletedAssoc = SemanticAssociationChange.builder()
                                                        .label("hasEquipment")
                                                        .iri("http://example.org#OldClass.hasEquipment")
                                                        .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                        .changes(List.of())
                                                        .build();

            var addedAssoc = SemanticAssociationChange.builder()
                                                      .label("hasEquipment")
                                                      .iri("http://example.org#NewClass.hasEquipment")
                                                      .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                      .changes(List.of())
                                                      .build();

            var domain = SemanticClassChange.builder()
                                            .label("NewClass")
                                            .iri("http://example.org#NewClass")
                                            .oldIRI("http://example.org#OldClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAssoc),
                      List.of(deletedAssoc),
                      domain
                                                              );

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).isInstanceOf(SemanticAssociationChange.class);
            assertThat(result.getFirst().getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.CHANGE);
        }
    }

    @Nested
    class MergeChangesTest {

        @Test
        void mergeChanges_emptyLists_returnsEmpty() {
            var result = RenameObjectBuilder.mergeChanges(List.of(), List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void mergeChanges_onlyAddedChanges_returnsAdded() {
            var addedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.COMMENT_CHANGE,
                      null,
                      "New comment"
                                                                 );

            var result = RenameObjectBuilder.mergeChanges(List.of(addedChange), List.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTo()).isEqualTo("New comment");
        }

        @Test
        void mergeChanges_onlyDeletedChanges_returnsDeleted() {
            var deletedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.COMMENT_CHANGE,
                      "Old comment",
                      null
                                                                   );

            var result = RenameObjectBuilder.mergeChanges(List.of(), List.of(deletedChange));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getFrom()).isEqualTo("Old comment");
        }

        @Test
        void mergeChanges_matchingChangeType_mergesValues() {
            var deletedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.SUPERCLASS_CHANGE,
                      "OldSuper",
                      "OldSuper"
                                                                   );

            var addedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.SUPERCLASS_CHANGE,
                      "NewSuper",
                      "NewSuper"
                                                                 );

            var result = RenameObjectBuilder.mergeChanges(
                      List.of(addedChange),
                      List.of(deletedChange)
                                                         );

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTo()).isEqualTo("NewSuper");
        }

        @Test
        void mergeChanges_sameValueAfterMerge_filtersOut() {
            var deletedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.DATATYPE_CHANGE,
                      "String",
                      "String"
                                                                   );

            var addedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.DATATYPE_CHANGE,
                      "String",
                      "String"
                                                                 );

            var result = RenameObjectBuilder.mergeChanges(
                      List.of(addedChange),
                      List.of(deletedChange)
                                                         );

            assertThat(result).isEmpty();
        }

        @Test
        void mergeChanges_domainChange_setsFromValue() {
            var deletedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.DOMAIN_CHANGE,
                      "http://example.org#OldClass",
                      "http://example.org#OldClass"
                                                                   );

            var addedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.DOMAIN_CHANGE,
                      "http://example.org#NewClass",
                      "http://example.org#NewClass"
                                                                 );

            var result = RenameObjectBuilder.mergeChanges(
                      List.of(addedChange),
                      List.of(deletedChange)
                                                         );

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getFrom()).isEqualTo("http://example.org#OldClass");
            assertThat(result.getFirst().getTo()).isEqualTo("http://example.org#NewClass");
        }

        @Test
        void mergeChanges_multipleChanges_mergesAll() {
            var deletedChange1 = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.COMMENT_CHANGE,
                      "Old comment",
                      null
                                                                    );

            var deletedChange2 = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.STEREOTYPE_REMOVED,
                      "Concrete",
                      null
                                                                    );

            var addedChange1 = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.COMMENT_CHANGE,
                      null,
                      "New comment"
                                                                  );

            var addedChange2 = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.STEREOTYPE_ADDED,
                      null,
                      "Abstract"
                                                                  );

            var result = RenameObjectBuilder.mergeChanges(
                      List.of(addedChange1, addedChange2),
                      List.of(deletedChange1, deletedChange2)
                                                         );

            assertThat(result).hasSize(3)
                              .anyMatch(change ->
                                                  change.getSemanticFieldChangeType() == SemanticFieldChangeType.COMMENT_CHANGE
                                       )
                              .anyMatch(change ->
                                                  change.getSemanticFieldChangeType() == SemanticFieldChangeType.STEREOTYPE_REMOVED
                                       )
                              .anyMatch(change ->
                                                  change.getSemanticFieldChangeType() == SemanticFieldChangeType.STEREOTYPE_ADDED
                                       );
        }

        @Test
        void mergeChanges_differentChangeTypes_keepsBoth() {
            var deletedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.DATATYPE_CHANGE,
                      "String",
                      "String"
                                                                   );

            var addedChange = ChangeObjectTestBuilder.fieldChange(
                      SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                      "0..1",
                      "0..1"
                                                                 );

            var result = RenameObjectBuilder.mergeChanges(
                      List.of(addedChange),
                      List.of(deletedChange)
                                                         );

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class DomainRenameHandlingTest {

        @Test
        void mergePropertyList_domainChangeMatchesOldIRI_convertsToDomainRename() {
            var deletedAttr = SemanticAttributeChange.builder()
                                                     .label("voltage")
                                                     .iri("http://example.org#OldClass.voltage")
                                                     .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                     .changes(List.of(
                                                               ChangeObjectTestBuilder.fieldChange(
                                                                         SemanticFieldChangeType.DOMAIN_CHANGE,
                                                                         "http://example.org#OldClass",
                                                                         "http://example.org#OldClass"
                                                                                                  )
                                                                     ))
                                                     .build();

            var addedAttr = SemanticAttributeChange.builder()
                                                   .label("voltage")
                                                   .iri("http://example.org#NewClass.voltage")
                                                   .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                   .changes(List.of(
                                                             ChangeObjectTestBuilder.fieldChange(
                                                                       SemanticFieldChangeType.DOMAIN_CHANGE,
                                                                       "http://example.org#NewClass",
                                                                       "http://example.org#NewClass"
                                                                                                )
                                                                   ))
                                                   .build();

            var domain = SemanticClassChange.builder()
                                            .label("NewClass")
                                            .iri("http://example.org#NewClass")
                                            .oldIRI("http://example.org#OldClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAttr),
                      List.of(deletedAttr),
                      domain
                                                              );

            assertThat(result).hasSize(1);
            var domainChange = result.getFirst().getChanges().stream()
                                     .filter(change -> change.getSemanticFieldChangeType() == SemanticFieldChangeType.DOMAIN_RENAME)
                                     .findFirst();

            assertThat(domainChange).isPresent();
        }

        @Test
        void mergePropertyList_domainChangeDoesNotMatchOldIRI_keepsDomainChange() {
            var deletedAttr = SemanticAttributeChange.builder()
                                                     .label("voltage")
                                                     .iri("http://example.org#SomeOtherClass.voltage")
                                                     .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                     .changes(List.of(
                                                               ChangeObjectTestBuilder.fieldChange(
                                                                         SemanticFieldChangeType.DOMAIN_CHANGE,
                                                                         "http://example.org#SomeOtherClass",
                                                                         "http://example.org#SomeOtherClass"
                                                                                                  )
                                                                     ))
                                                     .build();

            var addedAttr = SemanticAttributeChange.builder()
                                                   .label("voltage")
                                                   .iri("http://example.org#NewClass.voltage")
                                                   .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                   .changes(List.of(
                                                             ChangeObjectTestBuilder.fieldChange(
                                                                       SemanticFieldChangeType.DOMAIN_CHANGE,
                                                                       "http://example.org#NewClass",
                                                                       "http://example.org#NewClass"
                                                                                                )
                                                                   ))
                                                   .build();

            var domain = SemanticClassChange.builder()
                                            .label("NewClass")
                                            .iri("http://example.org#NewClass")
                                            .oldIRI("http://example.org#OldClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(addedAttr),
                      List.of(deletedAttr),
                      domain
                                                              );

            assertThat(result).hasSize(1);
            var domainChange = result.getFirst().getChanges().stream()
                                     .filter(change -> change.getSemanticFieldChangeType() == SemanticFieldChangeType.DOMAIN_CHANGE)
                                     .findFirst();

            assertThat(domainChange).isPresent();
            assertThat(domainChange.get().getFrom()).isEqualTo("http://example.org#SomeOtherClass");
        }
    }

    @Nested
    class EdgeCasesTest {

        @Test
        void createRenameObject_emptyChanges_worksCorrectly() {
            var deleted = ChangeObjectTestBuilder.resourceChange("OldName", SemanticResourceChangeType.DELETE);
            deleted.setIri("http://example.org#OldName");

            var added = ChangeObjectTestBuilder.resourceChange("NewName", SemanticResourceChangeType.ADD);
            added.setIri("http://example.org#NewName");

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.8);

            var result = RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result).isNotNull();
            assertThat(result.getSemanticResourceChangeType()).isEqualTo(SemanticResourceChangeType.RENAME);
            assertThat(result.getChanges()).isEmpty();
        }

        @Test
        void mergePropertyList_emptyLists_returnsEmpty() {
            var domain = SemanticClassChange.builder()
                                            .label("TestClass")
                                            .iri("http://example.org#TestClass")
                                            .build();

            var result = RenameObjectBuilder.mergePropertyList(
                      List.of(),
                      List.of(),
                      domain
                                                              );

            assertThat(result).isEmpty();
        }

        @Test
        void createRenameObject_classWithEnumEntries_mergesEnumEntries() {
            var deletedEntry = SemanticEnumEntryChange.builder()
                                                      .label("VALUE1")
                                                      .iri("http://example.org#OldEnum.VALUE1")
                                                      .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                                      .changes(List.of())
                                                      .build();

            var deleted = SemanticClassChange.builder()
                                             .label("OldEnum")
                                             .iri("http://example.org#OldEnum")
                                             .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                                             .changes(List.of())
                                             .attributes(List.of())
                                             .associations(List.of())
                                             .enumEntries(List.of(deletedEntry))
                                             .build();

            var addedEntry = SemanticEnumEntryChange.builder()
                                                    .label("VALUE1")
                                                    .iri("http://example.org#NewEnum.VALUE1")
                                                    .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                                    .changes(List.of())
                                                    .build();

            var added = SemanticClassChange.builder()
                                           .label("NewEnum")
                                           .iri("http://example.org#NewEnum")
                                           .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                                           .changes(List.of())
                                           .attributes(List.of())
                                           .associations(List.of())
                                           .enumEntries(List.of(addedEntry))
                                           .build();

            var renameCandidate = new RenameCandidate<>(deleted, added, 0.9);

            var result = (SemanticClassChange) RenameObjectBuilder.createRenameObject(renameCandidate);

            assertThat(result.getEnumEntries()).hasSize(1);
            assertThat(result.getEnumEntries().getFirst().getSemanticResourceChangeType())
                      .isEqualTo(SemanticResourceChangeType.CHANGE);
        }
    }
}

