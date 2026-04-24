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

package org.rdfarchitect.models.changes.semanticchanges;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.triplechanges.TripleClassChange;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Object collecting all relevant information about a change to a class, including its attributes,
 * associations, and enum entries and their renames.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public final class SemanticClassChange extends SemanticResourceChange {

    @Builder.Default private List<SemanticAttributeChange> attributes = new ArrayList<>();

    @Builder.Default private List<SemanticAssociationChange> associations = new ArrayList<>();

    @Builder.Default private List<SemanticEnumEntryChange> enumEntries = new ArrayList<>();

    @Builder.Default
    private List<RenameCandidate<SemanticAttributeChange>> attributeRenameCandidates =
            new ArrayList<>();

    @Builder.Default
    private List<RenameCandidate<SemanticAssociationChange>> associationRenameCandidates =
            new ArrayList<>();

    @Builder.Default
    private List<RenameCandidate<SemanticEnumEntryChange>> enumEntryRenameCandidates =
            new ArrayList<>();

    public SemanticClassChange(TripleClassChange tripleChange) {
        super(tripleChange);
        this.attributes = new ArrayList<>();
        this.associations = new ArrayList<>();
        this.enumEntries = new ArrayList<>();
        this.attributeRenameCandidates = new ArrayList<>();
        this.associationRenameCandidates = new ArrayList<>();
        this.enumEntryRenameCandidates = new ArrayList<>();
    }

    public SemanticClassChange(SemanticClassChange other) {
        super(other);
        this.attributes =
                other.attributes.stream()
                        .map(SemanticAttributeChange::copy)
                        .collect(Collectors.toCollection(ArrayList::new));
        this.associations =
                other.associations.stream()
                        .map(SemanticAssociationChange::copy)
                        .collect(Collectors.toCollection(ArrayList::new));
        this.enumEntries =
                other.enumEntries.stream()
                        .map(SemanticEnumEntryChange::copy)
                        .collect(Collectors.toCollection(ArrayList::new));
        this.attributeRenameCandidates = new ArrayList<>(other.attributeRenameCandidates);
        this.associationRenameCandidates = new ArrayList<>(other.associationRenameCandidates);
        this.enumEntryRenameCandidates = new ArrayList<>(other.enumEntryRenameCandidates);
    }

    public SemanticClassChange(Resource resource, SemanticResourceChangeType changeType) {
        super(resource, changeType);
        this.attributes = new ArrayList<>();
        this.associations = new ArrayList<>();
        this.enumEntries = new ArrayList<>();
        this.attributeRenameCandidates = new ArrayList<>();
        this.associationRenameCandidates = new ArrayList<>();
        this.enumEntryRenameCandidates = new ArrayList<>();
    }

    @Override
    public SemanticClassChange copy() {
        return new SemanticClassChange(this);
    }
}
