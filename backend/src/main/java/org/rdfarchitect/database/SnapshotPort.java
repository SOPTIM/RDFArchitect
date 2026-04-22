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

package org.rdfarchitect.database;

/** Port interface for snapshot management operations */
public interface SnapshotPort {

    /**
     * Creates a snapshot of the current state of the specified dataset
     *
     * @param datasetName Name of the dataset to be snapshot
     * @return The Base64 token under which the snapshot has been persisted in the database
     */
    String createSnapshot(String datasetName);

    /**
     * Fetches a snapshot from the database using the given Base64 token
     *
     * @param base64Token The Base64 token identifying the snapshot to load
     */
    void fetchSnapshot(String base64Token);

    /**
     * Checks whether a snapshot with the given Base64 token exists
     *
     * @param base64Token The Base64 token to check
     * @return true if a snapshot with the token exists, false otherwise
     */
    boolean snapshotExists(String base64Token);
}
