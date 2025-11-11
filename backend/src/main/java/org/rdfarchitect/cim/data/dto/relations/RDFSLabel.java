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

package org.rdfarchitect.cim.data.dto.relations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RDFSLabel {

    public RDFSLabel(String value) {
        this.value = value;
    }

    private String value = null;

    private String lang = null;

    public Literal asLangLiteral() {
        return ResourceFactory.createLangLiteral(value, lang);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RDFSLabel)) {
            return false;
        }
        return ((RDFSLabel) o).value.equals(value) && ((RDFSLabel) o).lang.equals(lang);
    }
}
