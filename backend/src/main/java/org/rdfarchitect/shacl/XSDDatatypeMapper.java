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

package org.rdfarchitect.shacl;

import lombok.experimental.UtilityClass;

import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.vocabulary.XSD;

import java.util.Map;

@UtilityClass
public class XSDDatatypeMapper {

    /**
     * CIM primitives whose name is not the local name of the datatype they stand for. Without
     * these, composing {@code xsd:<label>} invents datatypes such as {@code xsd:MonthDay} that no
     * validator knows and that contradict every official ENTSO-E constraint on the same attribute.
     */
    private static final Map<String, RDFDatatype> CIM_PRIMITIVE_ALIASES =
            Map.of(
                    "MonthDay", XSDDatatype.XSDgMonthDay,
                    "URI", XSDDatatype.XSDanyURI,
                    "IRI", XSDDatatype.XSDanyURI,
                    "StringIRI", XSDDatatype.XSDanyURI,
                    "URL", XSDDatatype.XSDanyURI,
                    "StringFixedLanguage", XSDDatatype.XSDstring,
                    "UUID", XSDDatatype.XSDstring,
                    "Version", XSDDatatype.XSDstring,
                    "LangString", RDFLangString.rdfLangString);

    public RDFDatatype classLabelToDatatype(String primitiveDatatypeClassLabel) {
        var alias = CIM_PRIMITIVE_ALIASES.get(primitiveDatatypeClassLabel);
        if (alias != null) {
            return alias;
        }
        var xsdUri = XSD.getURI() + primitiveDatatypeClassLabel;
        var typeListIterator = TypeMapper.getInstance().listTypes();
        while (typeListIterator.hasNext()) {
            var dt = typeListIterator.next();
            if (dt.getURI().equalsIgnoreCase(xsdUri)) {
                return dt;
            }
        }
        return new BaseDatatype(xsdUri);
    }

    /**
     * Returns the datatype for writing a value that has no language tag, such as a default value.
     * {@code rdf:langString} requires a language tag, so it is replaced by {@code xsd:string}; any
     * other datatype is returned unchanged.
     */
    public RDFDatatype datatypeForUntaggedValue(RDFDatatype datatype) {
        return datatype == RDFLangString.rdfLangString ? XSDDatatype.XSDstring : datatype;
    }

    /**
     * Returns whether a value without a language tag, such as a fixed or default value, can be
     * typed with {@code datatype}. This requires the datatype to be known to Jena and not to be
     * {@code rdf:langString}, which requires a language tag.
     */
    public boolean canTypeUntaggedValue(RDFDatatype datatype) {
        return datatype != RDFLangString.rdfLangString
                && TypeMapper.getInstance().getTypeByName(datatype.getURI()) != null;
    }
}
