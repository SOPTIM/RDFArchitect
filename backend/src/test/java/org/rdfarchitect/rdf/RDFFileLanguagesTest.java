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

package org.rdfarchitect.rdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;

class RDFFileLanguagesTest {

    @Test
    void of_extension_namesTheSyntax() {
        assertThat(RDFFileLanguages.of("EQ.ttl")).isEqualTo(Lang.TURTLE);
        assertThat(RDFFileLanguages.of("EQ.RDF")).isEqualTo(Lang.RDFXML);
    }

    @Test
    void of_hashInTheName_stillReadsTheExtension() {
        assertThat(RDFFileLanguages.of("EQ#2.ttl")).isEqualTo(Lang.TURTLE);
    }

    @Test
    void of_pathInFront_readsTheExtensionOfTheFile() {
        assertThat(RDFFileLanguages.of("profiles/v2.1/EQ.jsonld")).isEqualTo(Lang.JSONLD);
        assertThat(RDFFileLanguages.of("profiles.v2/EQ")).isNull();
    }

    @Test
    void of_noKnownExtension_isNull() {
        assertThat(RDFFileLanguages.of("EQ")).isNull();
        assertThat(RDFFileLanguages.of("EQ.")).isNull();
        assertThat(RDFFileLanguages.of("EQ.docx")).isNull();
        assertThat(RDFFileLanguages.of(null)).isNull();
    }
}
