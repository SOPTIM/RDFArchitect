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

package org.rdfarchitect.models.cim.rendering.mermaid.builder;

import org.rdfarchitect.models.cim.data.dto.CIMAttribute;

public class CIMAttributeToMermaidBuilder {

    private final CIMAttribute cimAttribute;

    public CIMAttributeToMermaidBuilder(CIMAttribute cimAttribute) {
        this.cimAttribute = cimAttribute;
    }

    public StringBuilder build() {
        var attribute = new StringBuilder()
                  .append(cimAttribute.getLabel().getValue())
                  .append(": ");

        if (cimAttribute.getDataType().getLabel() != null) {
            attribute.append(cimAttribute.getDataType().getLabel().getValue());
        } else {
            attribute.append(cimAttribute.getDataType().getUri().getSuffix());
        }

        if (cimAttribute.getMultiplicity() != null) {
            attribute.append(" [")
                     .append(cimAttribute.getMultiplicity().getUri().getSuffix().replace("M:", ""))
                     .append("]");
        }

        return attribute.append("\n");
    }
}
