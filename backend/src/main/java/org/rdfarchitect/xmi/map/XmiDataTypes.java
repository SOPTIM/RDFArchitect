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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.xmi.read.XmiAttribute;
import org.rdfarchitect.xmi.read.XmiIndex;

/**
 * What an attribute's type becomes, which is a choice of predicate rather than a label.
 *
 * <p>A {@link CIMSDataType} carrying {@code PRIMITIVE} is written as {@code cims:dataType} and
 * anything else as {@code rdfs:range}, so getting the kind wrong does not mislabel the type, it
 * writes the wrong triple. The classifier the attribute points at decides which:
 *
 * <ul>
 *   <li>stereotyped {@code CIMDatatype}, {@code Primitive} or {@code Compound} — a datatype
 *   <li>an enumeration — a range, because an attribute of that type takes one of its entries
 *   <li>anything else — a range, which is how an association-like attribute to a class reads
 * </ul>
 *
 * <p>{@code Compound} is in that list because leaving it out is measurable: the Geographical
 * Location profile reproduces its release on names while four of its attributes came out with
 * {@code rdfs:range} where the release writes {@code cims:dataType}.
 *
 * <p>An attribute may also point at an Enterprise Architect built-in type, which carries no
 * stereotype and is not part of any profile. A release never writes an {@code xsd:} IRI for one; it
 * points at the profile's own {@code Primitive} class of the same name, so that is what is looked
 * for.
 */
@RequiredArgsConstructor
class XmiDataTypes {

    private final XmiIndex index;
    private final XmiUris uris;

    /**
     * The type of an attribute, or {@code null} when the export states none that can be resolved —
     * a few attributes of every release point at a classifier that is not in the file.
     */
    CIMSDataType of(XmiAttribute attribute) {
        var type = index.getClasses().get(attribute.getTypeId());
        if (type == null) {
            return null;
        }
        var uri = new URI(uris.of(type));
        var label = new RDFSLabel(type.getName(), "en");
        return XmiStereotypes.isDatatype(type.getStereotypes()) && !type.isEnumeration()
                ? new CIMSPrimitiveDataType(uri, label)
                : new RDFSRange(uri, label);
    }

    /** Whether a classifier is one of the datatype kinds, which is also what keeps it concrete. */
    static boolean isDatatypeStereotype(String stereotype) {
        return CIMStereotypes.cimDatatypeString.equals(stereotype)
                || CIMStereotypes.primitiveString.equals(stereotype)
                || CIMStereotypes.compoundString.equals(stereotype);
    }
}
