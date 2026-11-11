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

package org.rdfarchitect.xmi.map;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.xmi.read.XmiExtensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What an element's stereotypes become.
 *
 * <p>Nearly all of them are passed through unchanged, including the ones RDFArchitect does not
 * model: a release keeps values like {@code NC}, {@code profcim} and {@code deprecated}, and CGMES
 * 3.0 uses a few dozen more. They are stored, rendered, exported and edited whether or not anything
 * knows what they mean, which is a supported state rather than a fallback.
 *
 * <p>Three are dropped and one is translated. {@code abstract} is not a stereotype RDFArchitect has
 * — it states the opposite, {@code concrete}, derived from the same flag — while {@code attribute}
 * and {@code enum} are written by the writer itself, so passing them through would duplicate the
 * first and silently replace the second. {@code enumeration} is stated as a bare word and stored as
 * an IRI.
 *
 * <p>Whether a value becomes an IRI or a plain literal is otherwise not decided here. It is decided
 * by what the value looks like, in one place, where the writer puts it into the graph.
 */
@UtilityClass
class XmiStereotypes {

    /** What an export may state and the writer must not be given. */
    private final Set<String> NOT_PASSED_THROUGH = Set.of("abstract", "attribute", "enum");

    /**
     * The name of the enumeration stereotype as an export spells it, which is not how it is stored.
     */
    private final String ENUMERATION = "enumeration";

    /**
     * The aggregate marker, which a release writes as a {@code ...NonStandard/UML#} IRI like the
     * enumeration and concrete markers rather than as the bare word an export would suggest.
     */
    final String OF_AGGREGATE = "http://iec.ch/TC57/NonStandard/UML#ofAggregate";

    /**
     * The stereotypes of a class or an attribute, in the order the export applies them.
     *
     * <p>{@code enumeration} is the one that changes on the way. An export states it as a bare
     * word, a release states it as a {@code ...NonStandard/UML#} IRI, and the difference decides
     * whether the datatype checks and the rendering recognise the class at all — passed through as
     * written it would be stored as a plain literal and mean nothing.
     */
    List<CIMSStereotype> of(Set<String> applied) {
        var stereotypes = new ArrayList<CIMSStereotype>();
        for (var stereotype : applied) {
            if (NOT_PASSED_THROUGH.contains(stereotype)) {
                continue;
            }
            stereotypes.add(
                    new CIMSStereotype(
                            ENUMERATION.equals(stereotype)
                                    ? CIMStereotypes.enumerationString
                                    : stereotype));
        }
        return stereotypes;
    }

    /**
     * The stereotypes of a class, with the concrete flag on top. Enterprise Architect states
     * whether a class is <em>abstract</em> and RDFArchitect states whether it is <em>concrete</em>,
     * so the flag is inverted; a datatype never carries it, enumerations included.
     */
    List<CIMSStereotype> ofClass(Set<String> applied, boolean isAbstract, boolean isDatatype) {
        var stereotypes = of(applied);
        if (!isAbstract && !isDatatype) {
            stereotypes.add(new CIMSStereotype(CIMStereotypes.concreteString));
        }
        return stereotypes;
    }

    /** Whether these stereotypes make a classifier a datatype rather than a class. */
    boolean isDatatype(Set<String> applied) {
        return applied.contains(CIMStereotypes.cimDatatypeString)
                || applied.contains(CIMStereotypes.primitiveString)
                || applied.contains(CIMStereotypes.compoundString)
                || applied.contains(ENUMERATION);
    }

    /**
     * Whether these stereotypes put a resource in the extension namespace rather than the model.
     */
    boolean isExtension(Set<String> applied) {
        return XmiExtensions.isExtension(applied);
    }
}
