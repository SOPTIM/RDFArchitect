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

import java.util.UUID;

public interface LocateClassUseCase {

    /** A class of one graph of the dataset. */
    record LocatedClass(String graphUri, String classUri, UUID classUUID) {}

    /**
     * Finds the class a uuid stands for. The uuid is either the one a class carries in one of the
     * graphs, or the uuid of a merged class of the cross profile view, which belongs to no graph
     * and is derived from the class uri instead. A merged class is located in the first graph that
     * defines it, in the order the schemas are listed in.
     *
     * @throws IllegalArgumentException when no graph of the dataset knows the uuid
     */
    LocatedClass locate(String datasetName, String classUUID);
}
