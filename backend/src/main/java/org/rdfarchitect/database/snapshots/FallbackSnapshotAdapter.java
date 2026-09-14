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

package org.rdfarchitect.database.snapshots;

import org.rdfarchitect.database.SnapshotPort;
import org.rdfarchitect.exception.database.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SnapshotPort} that prefers a persistent primary store and transparently falls back to a
 * secondary one when the primary is unavailable. New snapshots go to the primary whenever it is
 * reachable; lookups check the fallback first (its tokens are unknown to the primary) and the
 * primary otherwise.
 *
 * <p>{@link SnapshotPort#isAvailable()} is only a hint: a store can answer a reachability probe and
 * still reject the operation itself — a Fuseki behind the default {@code shiro.ini} serves {@code
 * /$/ping} anonymously but restricts the rest of {@code /$/**} to localhost, so a containerised
 * backend gets a green probe and a 403 on dataset creation. Primary failures are therefore treated
 * the same as an unreachable primary rather than propagated to the caller.
 */
public class FallbackSnapshotAdapter implements SnapshotPort {

    private static final Logger logger = LoggerFactory.getLogger(FallbackSnapshotAdapter.class);

    private final SnapshotPort primary;
    private final SnapshotPort fallback;

    public FallbackSnapshotAdapter(SnapshotPort primary, SnapshotPort fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String createSnapshot(String datasetName) {
        if (primary.isAvailable()) {
            try {
                return primary.createSnapshot(datasetName);
            } catch (DatabaseException e) {
                logger.warn(
                        "Primary snapshot store reported itself available but rejected the snapshot"
                                + " for dataset '{}' - falling back to in-memory storage.",
                        datasetName,
                        e);
            }
        } else {
            logger.warn(
                    "Primary snapshot store is unavailable - storing snapshot for dataset '{}' in"
                            + " memory. It will not survive a backend restart.",
                    datasetName);
        }
        return fallback.createSnapshot(datasetName);
    }

    @Override
    public void fetchSnapshot(String base64Token) {
        if (fallback.snapshotExists(base64Token)) {
            fallback.fetchSnapshot(base64Token);
            return;
        }
        primary.fetchSnapshot(base64Token);
    }

    @Override
    public boolean snapshotExists(String base64Token) {
        if (fallback.snapshotExists(base64Token)) {
            return true;
        }
        if (!primary.isAvailable()) {
            return false;
        }
        try {
            return primary.snapshotExists(base64Token);
        } catch (DatabaseException e) {
            logger.warn("Primary snapshot store could not be queried for a token.", e);
            return false;
        }
    }
}
