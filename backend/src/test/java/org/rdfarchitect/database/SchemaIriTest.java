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

package org.rdfarchitect.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.irix.IRIx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SchemaIriTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"http://graph#my-profile", "my profile", "a%b#c&d+e", "Équipement", "3.0"})
    void of_anyName_isAnAbsoluteIriThatGivesTheNameBack(String name) {
        var iri = SchemaIri.of(name);

        assertThat(IRIx.create(iri).isAbsolute()).isTrue();
        assertThat(SchemaIri.toName(iri)).isEqualTo(name);
    }

    @Test
    void toName_iriStoredBeforeGraphsWereNamed_isItsOwnName() {
        assertThat(SchemaIri.toName("http://graph#my_profile"))
                .isEqualTo("http://graph#my_profile");
    }
}
