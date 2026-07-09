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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SnapshotPort} that prefers a persistent primary store and transparently falls back to a
 * secondary one when the primary is unavailable. New snapshots go to the primary whenever it is
 * reachable; lookups check the fallback first (its tokens are unknown to the primary) and the
 * primary otherwise.
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
            return primary.createSnapshot(datasetName);
        }
        logger.warn(
                "Primary snapshot store is unavailable - storing snapshot for dataset '{}' in"
                        + " memory. It will not survive a backend restart.",
                datasetName);
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
        return fallback.snapshotExists(base64Token)
                || (primary.isAvailable() && primary.snapshotExists(base64Token));
    }
}
