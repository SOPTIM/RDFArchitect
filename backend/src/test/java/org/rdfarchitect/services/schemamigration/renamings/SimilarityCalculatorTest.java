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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.services.schemamigration.ChangeObjectTestBuilder;

import java.util.List;

class SimilarityCalculatorTest {
    @Nested
    class CalculateClassSimilarityTest {

        @Test
        void calculateSimilarity_classesWithSameSuperclass_returnsHighScore() {
            var deleted =
                    SemanticClassChange.builder()
                            .label("OldClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                                                    "Equipment",
                                                    "Equipment")))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("NewClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                                                    "Equipment",
                                                    "Equipment")))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_classesWithDifferentSuperclass_returnsLowerScore() {
            var deleted =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                                                    "EquipmentA",
                                                    "EquipmentA")))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                                                    "EquipmentB",
                                                    "EquipmentB")))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isLessThan(1.0);
        }

        @Test
        void calculateSimilarity_classesWithMatchingStereotypes_returnsHighScore() {
            var deleted =
                    SemanticClassChange.builder()
                            .label("OldClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.STEREOTYPE_REMOVED,
                                                    "Concrete",
                                                    null)))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("NewClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.STEREOTYPE_ADDED,
                                                    null,
                                                    "Concrete")))
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_classesWithMatchingProperties_returnsHighScore() {
            var attr1 =
                    SemanticAttributeChange.builder()
                            .label("voltage")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .build();

            var attr2 =
                    SemanticAttributeChange.builder()
                            .label("current")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .build();

            var deleted =
                    SemanticClassChange.builder()
                            .label("OldClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .attributes(List.of(attr1, attr2))
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var attr3 =
                    SemanticAttributeChange.builder()
                            .label("voltage")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .build();

            var attr4 =
                    SemanticAttributeChange.builder()
                            .label("current")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("NewClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .attributes(List.of(attr3, attr4))
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_classesWithNoMatchingProperties_returnsLowerScore() {
            var attr1 =
                    SemanticAttributeChange.builder()
                            .label("oldProperty")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .build();

            var deleted =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .attributes(List.of(attr1))
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var attr2 =
                    SemanticAttributeChange.builder()
                            .label("newProperty")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .attributes(List.of(attr2))
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isLessThan(1.0);
        }

        @Test
        void calculateSimilarity_classesWithNoProperties_returnsScoreBasedOnLabel() {
            var deleted =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(List.of())
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var added =
                    SemanticClassChange.builder()
                            .label("TestClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(List.of())
                            .attributes(List.of())
                            .associations(List.of())
                            .enumEntries(List.of())
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.9);
        }
    }

    @Nested
    class CalculateAttributeSimilarityTest {

        @Test
        void calculateSimilarity_attributesWithSameDatatype_returnsHighScore() {
            var deleted =
                    SemanticAttributeChange.builder()
                            .label("OldAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "String",
                                                    "String")))
                            .build();

            var added =
                    SemanticAttributeChange.builder()
                            .label("NewAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "String",
                                                    "String")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_attributesWithDifferentDatatype_returnsLowerScore() {
            var deleted =
                    SemanticAttributeChange.builder()
                            .label("TestAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "String",
                                                    "String")))
                            .build();

            var added =
                    SemanticAttributeChange.builder()
                            .label("TestAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "Integer",
                                                    "Integer")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isLessThan(1.0);
        }

        @Test
        void calculateSimilarity_attributesWithSameMultiplicity_contributesToScore() {
            var deleted =
                    SemanticAttributeChange.builder()
                            .label("OldAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..1",
                                                    "0..1")))
                            .build();

            var added =
                    SemanticAttributeChange.builder()
                            .label("NewAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..1",
                                                    "0..1")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_attributesWithSameDefaultValue_contributesToScore() {
            var deleted =
                    SemanticAttributeChange.builder()
                            .label("OldAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DEFAULT_VALUE_CHANGE,
                                                    "100",
                                                    "100")))
                            .build();

            var added =
                    SemanticAttributeChange.builder()
                            .label("NewAttr")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DEFAULT_VALUE_CHANGE,
                                                    "100",
                                                    "100")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_attributesWithAllMatchingProperties_returnsVeryHighScore() {
            var deleted =
                    SemanticAttributeChange.builder()
                            .label("voltage")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "Float",
                                                    "Float"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "1..1",
                                                    "1..1"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DEFAULT_VALUE_CHANGE,
                                                    "0.0",
                                                    "0.0")))
                            .build();

            var added =
                    SemanticAttributeChange.builder()
                            .label("voltage")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DATATYPE_CHANGE,
                                                    "Float",
                                                    "Float"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "1..1",
                                                    "1..1"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.DEFAULT_VALUE_CHANGE,
                                                    "0.0",
                                                    "0.0")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.9);
        }
    }

    @Nested
    class CalculateAssociationSimilarityTest {

        @Test
        void calculateSimilarity_associationsWithSameTarget_returnsHighScore() {
            var deleted =
                    SemanticAssociationChange.builder()
                            .label("OldAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "TargetClass",
                                                    "TargetClass")))
                            .build();

            var added =
                    SemanticAssociationChange.builder()
                            .label("NewAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "TargetClass",
                                                    "TargetClass")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_associationsWithDifferentTarget_returnsLowerScore() {
            var deleted =
                    SemanticAssociationChange.builder()
                            .label("TestAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "TargetA",
                                                    "TargetA")))
                            .build();

            var added =
                    SemanticAssociationChange.builder()
                            .label("TestAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "TargetB",
                                                    "TargetB")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isLessThan(1.0);
        }

        @Test
        void calculateSimilarity_associationsWithSameMultiplicity_contributesToScore() {
            var deleted =
                    SemanticAssociationChange.builder()
                            .label("OldAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..*",
                                                    "0..*")))
                            .build();

            var added =
                    SemanticAssociationChange.builder()
                            .label("NewAssoc")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..*",
                                                    "0..*")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.5);
        }

        @Test
        void calculateSimilarity_associationsWithAllMatchingProperties_returnsVeryHighScore() {
            var deleted =
                    SemanticAssociationChange.builder()
                            .label("hasEquipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "Equipment",
                                                    "Equipment"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..*",
                                                    "0..*"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.ASSOCIATION_USED_CHANGE,
                                                    "true",
                                                    "true")))
                            .build();

            var added =
                    SemanticAssociationChange.builder()
                            .label("hasEquipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .changes(
                                    List.of(
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.TARGET_CHANGE,
                                                    "Equipment",
                                                    "Equipment"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.MULTIPLICITY_CHANGE,
                                                    "0..*",
                                                    "0..*"),
                                            ChangeObjectTestBuilder.fieldChange(
                                                    SemanticFieldChangeType.ASSOCIATION_USED_CHANGE,
                                                    "true",
                                                    "true")))
                            .build();

            var similarity = SimilarityCalculator.calculateSimilarity(added, deleted);

            assertThat(similarity).isGreaterThan(0.9);
        }
    }
}
