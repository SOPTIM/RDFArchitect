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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

/**
 * Shared base for attribute value nodes such as {@link CIMSIsFixed} and {@link CIMSIsDefault}.
 *
 * <p>Holds the literal lexical {@code value}, an optional XSD {@code dataType} and a {@code
 * blankNode} flag indicating whether the value should be persisted as an RDF blank-node wrapper
 * rather than as a direct literal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AttributeValueNode {

    private String value;

    private URI dataType;

    private boolean blankNode;

    public Literal asLiteral() {
        if (dataType == null) {
            return ResourceFactory.createPlainLiteral(value);
        }
        return ResourceFactory.createTypedLiteral(
                value, TypeMapper.getInstance().getSafeTypeByName(dataType.toString()));
    }
}
