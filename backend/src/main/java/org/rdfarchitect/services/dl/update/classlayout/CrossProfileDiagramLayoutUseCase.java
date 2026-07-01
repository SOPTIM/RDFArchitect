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

package org.rdfarchitect.services.dl.update.classlayout;

import java.util.UUID;

public interface CrossProfileDiagramLayoutUseCase {

    /**
     * Migrates the CrossProfileDiagram layout entry for a class whose IRI has changed.
     *
     * @param datasetName the literal name of the dataset
     * @param oldMergedUuid the UUID derived from the old class IRI
     * @param newMergedUuid the UUID derived from the new class IRI
     * @param newClassUri the full IRI of the renamed class
     */
    void migrateLayoutToNewClassUri(
            String datasetName, UUID oldMergedUuid, UUID newMergedUuid, String newClassUri);
}
