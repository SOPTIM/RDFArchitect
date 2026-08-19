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

package org.rdfarchitect.services.select;

import org.rdfarchitect.api.dto.ClassSchemaOccurrenceDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;

public interface ListClassSchemaOccurrencesUseCase {

    /**
     * Reports for every graph of the dataset whether the class identified by uri is defined there.
     *
     * @param graphIdentifier the dataset name and graph uri the class uuid belongs to
     * @param classUUID the uuid of the class inside the given graph
     * @return one entry per graph of the dataset, in the order the graphs are stored in
     */
    List<ClassSchemaOccurrenceDTO> listSchemaOccurrences(
            GraphIdentifier graphIdentifier, String classUUID);
}
