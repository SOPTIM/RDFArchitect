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

package org.rdfarchitect.models.cim.data.dto.relations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CIMSIsFixed {

    public CIMSIsFixed(String value) {
        this.value = value;
    }

    private String value;

    private URI dataType;

    public Literal asLiteral() {
        if (dataType == null) {
            return ResourceFactory.createPlainLiteral(value);
        }
        return ResourceFactory.createTypedLiteral(value, TypeMapper.getInstance().getSafeTypeByName(dataType.toString()));
    }
}
