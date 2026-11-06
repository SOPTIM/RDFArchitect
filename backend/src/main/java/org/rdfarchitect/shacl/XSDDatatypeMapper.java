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
     * CIM primitives whose name is not the local name of the XSD datatype they stand for. Without
     * these, composing {@code xsd:<label>} invents datatypes such as {@code xsd:MonthDay} that no
     * validator knows and that contradict every official ENTSO-E constraint on the same attribute.
     * {@code LangString} is the one primitive outside XSD, so it is handled apart from these.
     */
    private static final Map<String, String> CIM_PRIMITIVE_ALIASES =
            Map.of(
                    "MonthDay", "gMonthDay",
                    "URI", "anyURI",
                    "IRI", "anyURI",
                    "StringIRI", "anyURI",
                    "URL", "anyURI",
                    "StringFixedLanguage", "string",
                    "UUID", "string",
                    "Version", "string");

    private static final String LANG_STRING = "LangString";

    public RDFDatatype classLabelToDatatype(String primitiveDatatypeClassLabel) {
        if (LANG_STRING.equals(primitiveDatatypeClassLabel)) {
            return RDFLangString.rdfLangString;
        }
        var localName =
                CIM_PRIMITIVE_ALIASES.getOrDefault(
                        primitiveDatatypeClassLabel, primitiveDatatypeClassLabel);
        var xsdUri = XSD.getURI() + localName;
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
     * The datatype to write a value of {@code datatype} with when that value is only text, as a
     * migration's default is: {@code rdf:langString} needs a language tag, so it is a plain string.
     */
    public RDFDatatype forPlainValue(RDFDatatype datatype) {
        return datatype == RDFLangString.rdfLangString ? XSDDatatype.XSDstring : datatype;
    }

    /**
     * Whether a value that is only text, as a fixed or default value is, can be typed with {@code
     * datatype}: it has to exist, and {@code rdf:langString} needs a language tag on top.
     */
    public boolean canTypeAPlainValue(RDFDatatype datatype) {
        return datatype != RDFLangString.rdfLangString
                && TypeMapper.getInstance().getTypeByName(datatype.getURI()) != null;
    }
}
