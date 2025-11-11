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

package org.rdfarchitect.cim.rendering.mermaid.builder;

import org.rdfarchitect.cim.data.dto.CIMAssociation;

import java.util.Map;
import java.util.UUID;

public class CIMAssociationToMermaidBuilder {

    private final CIMAssociation from;

    private final CIMAssociation to;

    private final Map<String, UUID> uriToUuidMap;

    public CIMAssociationToMermaidBuilder(CIMAssociation from, CIMAssociation to, Map<String, UUID> uriToUuidMap) {
        this.from = from;
        this.to = to;
        this.uriToUuidMap = uriToUuidMap;
    }

    public StringBuilder build() {
        var mermaidString = new StringBuilder()
                  .append("`")
                  .append(uriToUuidMap.get(from.getDomain().getUri().toString()))
                  .append("` \"")
                  .append(from.getMultiplicity().getUri().getSuffix())
                  .append("\" ");
        if (to.getAssociationUsed().toString().equals("Yes")) {
            mermaidString.append("<");
        }
        mermaidString.append("--");
        if (from.getAssociationUsed().toString().equals("Yes")) {
            mermaidString.append(">");
        }
        mermaidString
                  .append(" \"")
                  .append(to.getMultiplicity().getUri().getSuffix())
                  .append("\" `")
                  .append(uriToUuidMap.get(to.getDomain().getUri().toString()))
                  .append("`")
                  .append("\n");
        return mermaidString;
    }
}
