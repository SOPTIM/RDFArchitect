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

package org.rdfarchitect.services.validation.workspace.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;

class MultiplicitiesTest {

    private static final String CIMS_NS = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";

    private static String normalize(String notation) {
        return Multiplicities.normalize(new CIMSMultiplicity(CIMS_NS + notation));
    }

    @Test
    void normalize_singleBound_becomesRange() {
        assertThat(normalize("M:1")).isEqualTo("1..1");
        assertThat(normalize("M:1")).isEqualTo(normalize("M:1..1"));
    }

    @Test
    void normalize_openUpperBound_staysOpen() {
        assertThat(normalize("M:0..n")).isEqualTo("0..n");
        assertThat(normalize("M:1..n")).isEqualTo("1..n");
    }

    @Test
    void normalize_differentMultiplicities_stayDifferent() {
        assertThat(normalize("M:0..1")).isNotEqualTo(normalize("M:1..1"));
        assertThat(normalize("M:0..n")).isNotEqualTo(normalize("M:1..n"));
    }

    @Test
    void normalize_unknownNotation_isKeptAsItIs() {
        assertThat(normalize("0..1")).isEqualTo("0..1");
        assertThat(normalize("M:")).isEqualTo("M:");
        assertThat(normalize("M:some..thing")).isEqualTo("M:some..thing");
    }

    @Test
    void normalize_differentUnknownNotations_stayDifferent() {
        assertThat(normalize("0..1")).isNotEqualTo(normalize("1..1"));
    }
}
