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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class XSDDatatypeMapperTest {

    /**
     * This can be the first test in the JVM to touch Jena. Until Jena has initialised, {@code
     * TypeMapper.getInstance()} is null, and touching its {@code RDF} vocabulary breaks Jena for
     * the whole run.
     */
    @BeforeAll
    static void initialiseJena() {
        JenaSystem.init();
    }

    @ParameterizedTest
    @CsvSource({
        "Float, float",
        "String, string",
        "Boolean, boolean",
        "DateTime, dateTime",
        "DateTimeStamp, dateTimeStamp",
        "Duration, duration",
        "Integer, integer",
        "MonthDay, gMonthDay",
        "URI, anyURI",
        "IRI, anyURI",
        "StringIRI, anyURI",
        "URL, anyURI",
        "StringFixedLanguage, string",
        "UUID, string",
        "Version, string"
    })
    void mapsCimPrimitivesToTheXsdDatatypeTheOfficialConstraintsUse(String label, String local) {
        var datatype = XSDDatatypeMapper.classLabelToDatatype(label);

        assertThat(datatype.getURI()).isEqualTo(XSD.getURI() + local);
        // A datatype Jena knows, not one composed from the label.
        assertThat(TypeMapper.getInstance().getTypeByName(datatype.getURI())).isSameAs(datatype);
    }

    @Test
    void aLanguageTaggedStringIsTheRdfDatatypeNotAnXsdOne() {
        var datatype = XSDDatatypeMapper.classLabelToDatatype("LangString");

        assertThat(datatype.getURI()).isEqualTo(RDF.dtLangString.getURI());
        assertThat(TypeMapper.getInstance().getTypeByName(datatype.getURI())).isSameAs(datatype);
    }

    @Test
    void aPlainValueCanBeTypedOnlyWithADatatypeThatExistsAndNeedsNoLanguage() {
        assertThat(canTypeAPlainValue("String")).isTrue();
        assertThat(canTypeAPlainValue("MonthDay")).isTrue();
        // It would need a language tag, and a fixed or default value has only its text.
        assertThat(canTypeAPlainValue("LangString")).isFalse();
        assertThat(canTypeAPlainValue("SomethingNobodyDefined")).isFalse();
    }

    private static boolean canTypeAPlainValue(String label) {
        return XSDDatatypeMapper.canTypeAPlainValue(XSDDatatypeMapper.classLabelToDatatype(label));
    }
}
