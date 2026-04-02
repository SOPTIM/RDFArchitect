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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Object collecting all relevant information about a change to an attribute. Includes fields for specifying default values for data migration.
 * dataType, primitiveDataType, optional will be populated automatically when assigning default values, while forceDefaultValue and defaultValue can  be set by the user.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public final class SemanticAttributeChange extends SemanticResourceChange {

    private String dataType;

    private String primitiveDataType;

    private String defaultValue;

    private boolean optional;

    private boolean forceDefaultValue = false;

    @Builder.Default
    private List<String> allowedValues = new ArrayList<>();

    public SemanticAttributeChange(TripleResourceChange tripleChange) {
        super(tripleChange);
        this.allowedValues = new ArrayList<>();
    }

    public SemanticAttributeChange(SemanticAttributeChange other) {
        super(other);
        dataType = other.getDataType();
        primitiveDataType = other.getPrimitiveDataType();
        defaultValue = other.getDefaultValue();
        optional = other.isOptional();
        forceDefaultValue = other.isForceDefaultValue();
        allowedValues = new ArrayList<>(other.getAllowedValues());
    }

    public SemanticAttributeChange(SemanticResourceChange resourceChange) {
        super(resourceChange);
        allowedValues = new ArrayList<>();
    }

    public SemanticAttributeChange(Resource resource, SemanticResourceChangeType changeType) {
        super(resource, changeType);
    }

    @Override
    public SemanticAttributeChange copy() {
        return new SemanticAttributeChange(this);
    }
}
