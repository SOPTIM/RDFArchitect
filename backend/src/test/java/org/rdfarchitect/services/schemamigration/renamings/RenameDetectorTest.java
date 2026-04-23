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
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.services.schemamigration.ChangeObjectTestBuilder;

import java.util.List;

class RenameDetectorTest {

    @Nested
    class DetectClassRenamesTest {

        @Test
        void detectClassRenames_addAndDeleteExistWithHighSimilarity_returnsRename() {
            var deletedClass =
                    ChangeObjectTestBuilder.classChange(
                            "OldClass", SemanticResourceChangeType.DELETE);
            var addedClass =
                    ChangeObjectTestBuilder.classChange("NewClass", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(addedClass, deletedClass))
                        .thenReturn(0.9);

                var result = RenameDetector.detectClassRenames(List.of(deletedClass, addedClass));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getOldResource()).isEqualTo(deletedClass);
                assertThat(result.getFirst().getNewResource()).isEqualTo(addedClass);
            }
        }

        @Test
        void detectClassRenames_noAddedClasses_returnsEmpty() {
            var deletedClass =
                    ChangeObjectTestBuilder.classChange("Old", SemanticResourceChangeType.DELETE);

            var result = RenameDetector.detectClassRenames(List.of(deletedClass));

            assertThat(result).isEmpty();
        }

        @Test
        void detectClassRenames_noDeletedClasses_returnsEmpty() {
            var addedClass =
                    ChangeObjectTestBuilder.classChange("New", SemanticResourceChangeType.ADD);

            var result = RenameDetector.detectClassRenames(List.of(addedClass));

            assertThat(result).isEmpty();
        }

        @Test
        void detectClassRenames_multipleAddedClasses_matchesWithHighestSimilarity() {
            var deleted =
                    ChangeObjectTestBuilder.classChange(
                            "OldClass", SemanticResourceChangeType.DELETE);
            var added1 =
                    ChangeObjectTestBuilder.classChange(
                            "Candidate1", SemanticResourceChangeType.ADD);
            var added2 =
                    ChangeObjectTestBuilder.classChange(
                            "Candidate2", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted))
                        .thenReturn(0.5);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted))
                        .thenReturn(0.85);

                var result = RenameDetector.detectClassRenames(List.of(deleted, added1, added2));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getNewResource()).isEqualTo(added2);
            }
        }

        @Test
        void detectClassRenames_similarityBelowThreshold_returnsEmpty() {
            var deleted =
                    ChangeObjectTestBuilder.classChange("Old", SemanticResourceChangeType.DELETE);
            var added = ChangeObjectTestBuilder.classChange("New", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted))
                        .thenReturn(0.2);

                var result = RenameDetector.detectClassRenames(List.of(deleted, added));

                assertThat(result).isEmpty();
            }
        }

        @Test
        void detectClassRenames_multipleDeletedClassesWithSameAddedClass_onlyMatchesOnce() {
            var deleted1 =
                    ChangeObjectTestBuilder.classChange("Old1", SemanticResourceChangeType.DELETE);
            var deleted2 =
                    ChangeObjectTestBuilder.classChange("Old2", SemanticResourceChangeType.DELETE);

            var added = ChangeObjectTestBuilder.classChange("New", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                // Both deleted items prefer the same added
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted1))
                        .thenReturn(0.9);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted2))
                        .thenReturn(0.92);

                var result = RenameDetector.detectClassRenames(List.of(deleted1, deleted2, added));
                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getOldResource()).isEqualTo(deleted1);
                assertThat(result.getFirst().getNewResource()).isEqualTo(added);
            }
        }

        @Test
        void detectClassRenames_emptyList_returnsEmpty() {
            var result = RenameDetector.detectClassRenames(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void detectClassRenames_multipleIndependentRenames_returnsAll() {
            var deleted1 =
                    ChangeObjectTestBuilder.classChange("Old1", SemanticResourceChangeType.DELETE);
            var deleted2 =
                    ChangeObjectTestBuilder.classChange("Old2", SemanticResourceChangeType.DELETE);
            var added1 =
                    ChangeObjectTestBuilder.classChange("New1", SemanticResourceChangeType.ADD);
            var added2 =
                    ChangeObjectTestBuilder.classChange("New2", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                // First pair
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted1))
                        .thenReturn(0.85);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted1))
                        .thenReturn(0.3);

                // Second pair
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted2))
                        .thenReturn(0.4);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted2))
                        .thenReturn(0.88);

                var result =
                        RenameDetector.detectClassRenames(
                                List.of(deleted1, deleted2, added1, added2));

                assertThat(result).hasSize(2);
                assertThat(result)
                        .anyMatch(
                                r ->
                                        r.getOldResource().equals(deleted1)
                                                && r.getNewResource().equals(added1));
                assertThat(result)
                        .anyMatch(
                                r ->
                                        r.getOldResource().equals(deleted2)
                                                && r.getNewResource().equals(added2));
            }
        }

        @Test
        void detectClassRenames_moreDeletedThanAdded_returnsOnlyMatches() {
            var deleted1 =
                    ChangeObjectTestBuilder.classChange("Old1", SemanticResourceChangeType.DELETE);
            var deleted2 =
                    ChangeObjectTestBuilder.classChange("Old2", SemanticResourceChangeType.DELETE);
            var deleted3 =
                    ChangeObjectTestBuilder.classChange("Old3", SemanticResourceChangeType.DELETE);
            var added = ChangeObjectTestBuilder.classChange("New", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted1))
                        .thenReturn(0.9);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted2))
                        .thenReturn(0.5);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted3))
                        .thenReturn(0.3);

                var result =
                        RenameDetector.detectClassRenames(
                                List.of(deleted1, deleted2, deleted3, added));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getOldResource()).isEqualTo(deleted1);
            }
        }

        @Test
        void detectClassRenames_scoreStoredInRenameCandidate_containsCorrectScore() {
            var deleted =
                    ChangeObjectTestBuilder.classChange("Old", SemanticResourceChangeType.DELETE);
            var added = ChangeObjectTestBuilder.classChange("New", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted))
                        .thenReturn(0.87);

                var result = RenameDetector.detectClassRenames(List.of(deleted, added));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getConfidenceScore()).isEqualTo(0.87);
            }
        }
    }

    @Nested
    class DetectPropertyRenamingsTest {

        @Test
        void detectPropertyRenames_addAndDeleteExistWithHighSimilarity_returnsRename() {
            var deletedA =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldA", SemanticResourceChangeType.DELETE);
            var addedA =
                    ChangeObjectTestBuilder.resourceChange("NewA", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {
                mock.when(() -> SimilarityCalculator.calculateSimilarity(addedA, deletedA))
                        .thenReturn(0.9);

                var result = RenameDetector.detectPropertyRenames(List.of(deletedA, addedA));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getOldResource()).isEqualTo(deletedA);
                assertThat(result.getFirst().getNewResource()).isEqualTo(addedA);
            }
        }

        @Test
        void detectPropertyRenames_noAddedChanges_returnsEmpty() {
            var deletedA =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldA", SemanticResourceChangeType.DELETE);

            var result = RenameDetector.detectPropertyRenames(List.of(deletedA));

            assertThat(result).isEmpty();
        }

        @Test
        void detectPropertyRenames_noDeletedChanges_returnsEmpty() {
            var addedA =
                    ChangeObjectTestBuilder.resourceChange("NewA", SemanticResourceChangeType.ADD);

            var result = RenameDetector.detectPropertyRenames(List.of(addedA));

            assertThat(result).isEmpty();
        }

        @Test
        void detectPropertyRenames_multipleAddedProperties_matchesWithHighestSimilarity() {
            var deleted =
                    ChangeObjectTestBuilder.resourceChange(
                            "Old", SemanticResourceChangeType.DELETE);

            var added1 =
                    ChangeObjectTestBuilder.resourceChange("New1", SemanticResourceChangeType.ADD);
            var added2 =
                    ChangeObjectTestBuilder.resourceChange("New2", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted))
                        .thenReturn(0.6);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted))
                        .thenReturn(0.8);

                var result = RenameDetector.detectPropertyRenames(List.of(deleted, added1, added2));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getNewResource()).isEqualTo(added2);
            }
        }

        @Test
        void detectPropertyRenames_similarityBelowThreshold_returnsEmpty() {
            var deleted =
                    ChangeObjectTestBuilder.resourceChange(
                            "Old", SemanticResourceChangeType.DELETE);
            var added =
                    ChangeObjectTestBuilder.resourceChange("New", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted))
                        .thenReturn(0.3);

                var result = RenameDetector.detectPropertyRenames(List.of(deleted, added));

                assertThat(result).isEmpty();
            }
        }

        @Test
        void
                detectPropertyRenames_multipleDeletedPropertiesWithSameAddedProperty_onlyMatchesOnce() {
            var deleted1 =
                    ChangeObjectTestBuilder.resourceChange(
                            "Old1", SemanticResourceChangeType.DELETE);
            var deleted2 =
                    ChangeObjectTestBuilder.resourceChange(
                            "Old2", SemanticResourceChangeType.DELETE);

            var added =
                    ChangeObjectTestBuilder.resourceChange("NewA", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted1))
                        .thenReturn(0.9);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted2))
                        .thenReturn(0.95);

                var result =
                        RenameDetector.detectPropertyRenames(List.of(deleted1, deleted2, added));

                assertThat(result).hasSize(1);
            }
        }

        @Test
        void detectPropertyRenames_emptyList_returnsEmpty() {
            var result = RenameDetector.detectPropertyRenames(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void detectPropertyRenames_multipleIndependentRenames_returnsAll() {
            var deleted1 =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldProp1", SemanticResourceChangeType.DELETE);
            var deleted2 =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldProp2", SemanticResourceChangeType.DELETE);
            var added1 =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp1", SemanticResourceChangeType.ADD);
            var added2 =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp2", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                // First pair
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted1))
                        .thenReturn(0.91);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted1))
                        .thenReturn(0.2);

                // Second pair
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted2))
                        .thenReturn(0.25);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted2))
                        .thenReturn(0.93);

                var result =
                        RenameDetector.detectPropertyRenames(
                                List.of(deleted1, deleted2, added1, added2));

                assertThat(result).hasSize(2);
                assertThat(result)
                        .anyMatch(
                                r ->
                                        r.getOldResource().equals(deleted1)
                                                && r.getNewResource().equals(added1));
                assertThat(result)
                        .anyMatch(
                                r ->
                                        r.getOldResource().equals(deleted2)
                                                && r.getNewResource().equals(added2));
            }
        }

        @Test
        void detectPropertyRenames_moreAddedThanDeleted_returnsOnlyMatches() {
            var deleted =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldProp", SemanticResourceChangeType.DELETE);
            var added1 =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp1", SemanticResourceChangeType.ADD);
            var added2 =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp2", SemanticResourceChangeType.ADD);
            var added3 =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp3", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {

                mock.when(() -> SimilarityCalculator.calculateSimilarity(added1, deleted))
                        .thenReturn(0.88);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added2, deleted))
                        .thenReturn(0.4);
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added3, deleted))
                        .thenReturn(0.6);

                var result =
                        RenameDetector.detectPropertyRenames(
                                List.of(deleted, added1, added2, added3));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getNewResource()).isEqualTo(added1);
            }
        }

        @Test
        void detectPropertyRenames_scoreStoredInRenameCandidate_containsCorrectScore() {
            var deleted =
                    ChangeObjectTestBuilder.resourceChange(
                            "OldProp", SemanticResourceChangeType.DELETE);
            var added =
                    ChangeObjectTestBuilder.resourceChange(
                            "NewProp", SemanticResourceChangeType.ADD);

            try (MockedStatic<SimilarityCalculator> mock = mockStatic(SimilarityCalculator.class)) {
                mock.when(() -> SimilarityCalculator.calculateSimilarity(added, deleted))
                        .thenReturn(0.84);

                var result = RenameDetector.detectPropertyRenames(List.of(deleted, added));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getConfidenceScore()).isEqualTo(0.84);
            }
        }
    }
}
