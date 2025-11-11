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

package org.rdfarchitect.api.dto.ontology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OntologyEntry {

    private String iri;
    @JsonProperty("isIriEntry")
    private boolean iriEntry;
    private String datatypeIri;
    private String value;

    public OntologyEntry() {}

    @JsonIgnore
    public OntologyEntry(OntologyField field, String value) {
        this.iri = field.getIri();
        this.iriEntry = field.isIriEntry();
        this.datatypeIri = field.getDatatypeIri();
        this.value = value;
    }

    @JsonIgnore
    public boolean isValidEntry() {
        //namespace, label and value must not be null
        if (iri == null || value == null) {
            return false;
        }

        //if the value is an iri, datatypeIri must be null
        if (iriEntry) {
            return datatypeIri == null;
        }

        return true;
    }
}
