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

package org.rdfarchitect.models.changelog;

import lombok.Data;

import org.apache.jena.graph.Graph;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ChangeLogEntry {

    private UUID changeId;
    private LocalDateTime timestamp;
    private String message;
    private WeakReference<Graph> additions;
    private WeakReference<Graph> deletions;

    public ChangeLogEntry(String message, DeltaCompressible delta) {
        this.changeId = delta.getVersionId();
        this.timestamp = LocalDateTime.now();
        this.message = message;

        this.additions = new WeakReference<>(delta.getAdditions());
        this.deletions = new WeakReference<>(delta.getDeletions());

        var additionsGraph = this.additions.get();
        var deletionsGraph = this.deletions.get();
        if (additionsGraph == null || deletionsGraph == null) {
            return;
        }

        if (additionsGraph.isEmpty() && deletionsGraph.isEmpty()) {
            this.additions = new WeakReference<>(delta.getBase());
        }
    }
}
