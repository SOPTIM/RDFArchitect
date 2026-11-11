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
 *
 * <p>A type the export stubs rather than declares is written all the same, as an {@code rdfs:range}
 * pointing at a resource nothing defines. Of the 32 names a CGMES 3.0 profiles-only export stubs,
 * the library writes 19 as datatypes and 13 as enumerations, so neither predicate is the safe
 * guess; {@code rdfs:range} is the weaker claim of the two, saying what the values are without
 * calling the missing type primitive.
 */
@RequiredArgsConstructor
class XmiDataTypes {

    private final XmiIndex index;
    private final XmiUris uris;

    /**
     * What an attribute's type became.
     *
     * @param type what is written, {@code null} where the export names nothing that can be resolved
     * @param undeclared the name it points at where nothing in the export declares it, {@code null}
     *     otherwise
     */
    record Typing(CIMSDataType type, String undeclared) {

        static final Typing NONE = new Typing(null, null);

        static Typing declared(CIMSDataType type) {
            return new Typing(type, null);
        }
    }

    /** The type of an attribute, and whether the export declares what it points at. */
    Typing of(XmiAttribute attribute) {
        var type = index.getClasses().get(attribute.getTypeId());
        if (type == null) {
            return ofUndeclared(attribute.getTypeId());
        }
        var uri = new URI(uris.of(type));
        var label = new RDFSLabel(type.getName(), "en");
        return Typing.declared(
                XmiStereotypes.isDatatype(type.getStereotypes()) && !type.isEnumeration()
                        ? new CIMSPrimitiveDataType(uri, label)
                        : new RDFSRange(uri, label));
    }

    /**
     * The type of an attribute pointing at a classifier the model section does not declare.
     *
     * <p>One of Enterprise Architect's own datatypes becomes a {@code cims:dataType} without the
     * stereotype that decides this for a declared classifier, because UML has it: a {@code
     * uml:PrimitiveType} is primitive by definition and cannot be an enumeration. A stub carries
     * nothing to decide on, so it becomes a range pointing at a resource nothing defines.
     */
    private Typing ofUndeclared(String typeId) {
        var primitive = index.getPrimitiveTypeNames().get(typeId);
        if (primitive != null) {
            return Typing.declared(
                    new CIMSPrimitiveDataType(
                            new URI(uris.ofUndeclared(primitive)), new RDFSLabel(primitive, "en")));
        }
        var stub = index.getStubNames().get(typeId);
        if (stub == null) {
            return Typing.NONE;
        }
        return new Typing(
                new RDFSRange(new URI(uris.ofUndeclared(stub)), new RDFSLabel(stub, "en")), stub);
    }

    /** Whether a classifier is one of the datatype kinds, which is also what keeps it concrete. */
    static boolean isDatatypeStereotype(String stereotype) {
        return CIMStereotypes.cimDatatypeString.equals(stereotype)
                || CIMStereotypes.primitiveString.equals(stereotype)
                || CIMStereotypes.compoundString.equals(stereotype);
    }
}
