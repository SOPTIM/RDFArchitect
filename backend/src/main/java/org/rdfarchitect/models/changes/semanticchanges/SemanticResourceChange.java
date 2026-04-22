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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;

import java.util.ArrayList;
import java.util.List;

/** Base class for all semantic resource changes. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SemanticClassChange.class, name = "classChange"),
    @JsonSubTypes.Type(value = SemanticPackageChange.class, name = "packageChange"),
    @JsonSubTypes.Type(value = SemanticAttributeChange.class, name = "attributeChange"),
    @JsonSubTypes.Type(value = SemanticResourceChange.class, name = "genericResourceChange"),
    @JsonSubTypes.Type(value = SemanticAssociationChange.class, name = "associationChange"),
    @JsonSubTypes.Type(value = SemanticEnumEntryChange.class, name = "enumEntryChange")
})
public sealed class SemanticResourceChange
        permits SemanticClassChange,
                SemanticPackageChange,
                SemanticAttributeChange,
                SemanticAssociationChange,
                SemanticEnumEntryChange {

    protected SemanticResourceChangeType semanticResourceChangeType;

    protected String label;

    // only set in case of a rename
    protected String oldIRI;

    protected String iri;

    @Builder.Default protected List<SemanticFieldChange> changes = new ArrayList<>();

    public SemanticResourceChange(String label) {
        this.label = label;
        this.changes = new ArrayList<>();
    }

    public SemanticResourceChange(TripleResourceChange tripleChange) {
        this.label = tripleChange.getLabel();
        this.iri = tripleChange.getUri();
        this.changes = new ArrayList<>();
    }

    public SemanticResourceChange(SemanticResourceChange other) {
        this.label = other.getLabel();
        this.oldIRI = other.getOldIRI();
        this.iri = other.getIri();
        this.semanticResourceChangeType = other.getSemanticResourceChangeType();
        this.changes = other.getChanges();
    }

    public SemanticResourceChange(Resource resource, SemanticResourceChangeType changeType) {
        this.semanticResourceChangeType = changeType;
        this.label = resource.getProperty(RDFS.label).getString();
        this.iri = resource.getURI();
        this.changes = new ArrayList<>();
    }

    public SemanticResourceChange copy() {
        return new SemanticResourceChange(this);
    }
}
