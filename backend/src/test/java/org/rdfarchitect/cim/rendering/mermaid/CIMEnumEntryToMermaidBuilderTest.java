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

package org.rdfarchitect.cim.rendering.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMEnumEntryToMermaidBuilder;

import static org.assertj.core.api.Assertions.*;

class CIMEnumEntryToMermaidBuilderTest {

    @ParameterizedTest
    @ValueSource(strings = {"enumEntry", "var", "x", "1", "\"sdf!§$", ""})
    void build_validEnumEntry_returnsMermaidEnumEntry(String enumEntryLabel) {
        //Arrange
        var enumEntry = CIMEnumEntry.builder()
                                    .label(new RDFSLabel(enumEntryLabel))
                                    .build();
        var builder = new CIMEnumEntryToMermaidBuilder(enumEntry);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(enumEntryLabel + "\n");
    }

    @Test
    void build_validEnumEntryWithNullLabel_throwsException() {
        //Arrange
        var enumEntry = CIMEnumEntry.builder()
                                    .label(null)
                                    .build();
        var builder = new CIMEnumEntryToMermaidBuilder(enumEntry);

        //Act/Assert
        assertThatException().isThrownBy(builder::build);
    }
}
