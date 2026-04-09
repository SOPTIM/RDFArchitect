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

package org.rdfarchitect.context;

import lombok.Data;
import org.apache.jena.graph.Graph;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.triplechanges.TripleClassChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;

import java.util.List;

@Data
public class SchemaMigrationContext {

    // original graph of the schema
    private Graph originalSchema;

    // graph of the updated schema
    private Graph updatedSchema;

    // original diff as triples
    private List<TripleClassChange> tripleDiff;

    // original diff as semantic changes
    private List<SemanticClassChange> semanticDiff;

    // list of detected/confirmed class renames
    private List<RenameCandidate<SemanticClassChange>> renameCandidates;

    // list of semantic class changes after the class renames are confirmed and the change objects are merged
    private List<SemanticClassChange> diffAfterClassConfirm;

    // list of the semantic class changes after class properties etc. are confirmed and merged
    private List<SemanticClassChange> diffAfterPropertyConfirm;

    //list of semantic class changes after attributes are enriched with default data
    private List<SemanticClassChange> diffAfterDefaultValueConfirm;

    public void clear() {
        this.originalSchema = null;
        this.updatedSchema = null;
        this.tripleDiff = null;
        this.semanticDiff = null;
        this.renameCandidates = null;
        this.diffAfterClassConfirm = null;
        this.diffAfterPropertyConfirm = null;
        this.diffAfterDefaultValueConfirm = null;
    }
}
