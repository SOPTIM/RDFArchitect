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
import org.apache.jena.vocabulary.XSD;

import java.util.Map;

@UtilityClass
public class XSDDatatypeMapper {

    /**
     * CIM primitives whose name is not the local name of the XSD datatype they stand for. Without
     * these, composing {@code xsd:<label>} invents datatypes such as {@code xsd:MonthDay} that no
     * validator knows and that contradict every official ENTSO-E constraint on the same attribute.
     */
    private static final Map<String, String> CIM_PRIMITIVE_ALIASES =
            Map.of(
                    "MonthDay", "gMonthDay",
                    "URI", "anyURI",
                    "IRI", "anyURI",
                    "StringIRI", "anyURI");

    public RDFDatatype classLabelToDatatype(String primitiveDatatypeClassLabel) {
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
}
