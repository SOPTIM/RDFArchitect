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

import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CIMClassToMermaidBuilder {

    private final UUID uuid;

    private final CIMClass cimClass;

    private List<StringBuilder> mermaidClassContents;

    private StringBuilder mermaidString;

    private static final String TAB = "    ";

    public CIMClassToMermaidBuilder(CIMClass cimClass, UUID uuid) {
        this.cimClass = cimClass;
        this.uuid = uuid;
    }

    public StringBuilder build() {
        mermaidString = new StringBuilder()
                  .append("class `")
                  .append(uuid)
                  .append("`[\"")
                  .append(cimClass.getLabel().getValue())
                  .append("\"]")
                  .append("{\n");
        appendClassStereotypes();
        appendClassContents();
        mermaidString.append("}\n");
        return mermaidString;
    }

    /**
     * Appends class contents like attributes or enum entries to be displayed in the mermaid class String.
     *
     * @param classContents The contents of the class.
     *
     * @return The builder.
     */
    public CIMClassToMermaidBuilder appendClassContents(List<StringBuilder> classContents) {
        classContents = new ArrayList<>(classContents);
        classContents.sort(StringBuilder::compareTo);
        this.mermaidClassContents = classContents;
        return this;
    }

    /**
     * Appends the stereotypes to the mermaid class String.
     */
    private void appendClassStereotypes() {
        var stereotypes = cimClass.getStereotypes();
        var stereotypesToRender = new ArrayList<String>();
        if (CollectionUtils.isEmpty(stereotypes) || !stereotypes.contains(new CIMSStereotype(CIMStereotypes.concrete.toString()))) {
            stereotypesToRender.add("abstract");
        }

        stereotypes.forEach(stereotype -> {
            if (!stereotype.toString().equals(CIMStereotypes.concrete.toString())) {
                stereotypesToRender.add(getShortenedStereotypeName(stereotype.toString()));
            }
        });
        if (stereotypesToRender.isEmpty()) {
            return;
        }
        stereotypesToRender.sort(String::compareTo);
        mermaidString
                  .append(TAB)
                  .append("<<")
                  .append(String.join(", ", stereotypesToRender))
                  .append(">>")
                  .append("\n");
    }

    /**
     * Appends the classContents to the mermaid String.
     */
    private void appendClassContents() {
        if (CollectionUtils.isEmpty(mermaidClassContents)) {
            return;
        }
        mermaidClassContents.forEach(content -> mermaidString
                  .append(TAB)
                  .append(content));
    }

    /**
     * If the stereotype is a known uri: retrieves a shortened name,
     * Otherwise returns the full string representation of the stereotype.
     *
     * @param stereotype The String containing the stereotype uri.
     *
     * @return The name of the stereotype.
     */
    private String getShortenedStereotypeName(String stereotype) {
        if (stereotype.equals(CIMStereotypes.enumeration.toString())) {
            return CIMStereotypes.enumeration.getLocalName();
        }
        return stereotype;
    }
}
