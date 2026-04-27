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

package org.rdfarchitect.services.compare;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Interface for comparing two schemas. */
public interface SchemaComparisonUseCase {

    /**
     * Compares the schema of the given file with the schema of the graph identified by the given
     * identifier. Changes in UUID are ignored, as they are currently only present in the
     * InMemoryGraph.
     *
     * @param graphIdentifier The identifier of the graph to compare against.
     * @param file The file containing the schema to compare.
     * @return A list changes structured by Package and Class representing the differences between
     *     the schemas.
     */
    List<TriplePackageChange> compareSchemas(GraphIdentifier graphIdentifier, MultipartFile file);

    /**
     * Compares the schemas of two graphs identified by the given identifiers. Changes in UUID are
     * ignored, as they are currently only present in the InMemoryGraph.
     *
     * @param graphIdentifier The identifier of the first graph to compare.
     * @param otherGraphIdentifier The identifier of the second graph to compare.
     * @return A list changes structured by Package and Class representing the differences between
     *     the schemas.
     */
    List<TriplePackageChange> compareSchemas(
            GraphIdentifier graphIdentifier, GraphIdentifier otherGraphIdentifier);

    /**
     * Compares the schemas of two files. Changes in UUID are ignored, as they are currently only
     * present in the InMemoryGraph.
     *
     * @param file1 The first file containing a schema to compare.
     * @param file2 The second file containing a schema to compare.
     * @return A list changes structured by Package and Class representing the differences between
     *     the schemas.
     */
    List<TriplePackageChange> compareSchemas(MultipartFile file1, MultipartFile file2);
}
