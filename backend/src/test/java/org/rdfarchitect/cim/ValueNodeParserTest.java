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

package org.rdfarchitect.cim;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class ValueNodeParserTest {

    @Test
    void parse_literalNode_readsValueAndDatatype() {
        Model model = ModelFactory.createDefaultModel();
        var literal = model.createTypedLiteral("42", XSD.integer.getURI());

        var parsed = ValueNodeParser.parse(literal);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("42"),
                  () -> assertThat(parsed.dataType()).isEqualTo(new URI(XSD.integer.getURI())),
                  () -> assertThat(parsed.blankNode()).isFalse(),
                  () -> assertThat(parsed.blankNodePredicate()).isNull(),
                  () -> assertThat(parsed.uriValue()).isFalse()
                 );
    }

    @Test
    void parse_uriNode_readsUriValue() {
        Model model = ModelFactory.createDefaultModel();
        var resource = model.createResource("http://example.com#value");

        var parsed = ValueNodeParser.parse(resource);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("http://example.com#value"),
                  () -> assertThat(parsed.dataType()).isNull(),
                  () -> assertThat(parsed.blankNode()).isFalse(),
                  () -> assertThat(parsed.blankNodePredicate()).isNull(),
                  () -> assertThat(parsed.uriValue()).isTrue()
                 );
    }

    @Test
    void parse_blankNodeLiteral_readsPredicateAndLiteral() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        var predicate = model.createProperty("http://example.com#predicate");
        blank.addProperty(predicate, model.createLiteral("blank"));

        var parsed = ValueNodeParser.parse(blank);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("blank"),
                  () -> assertThat(parsed.dataType()).isEqualTo(new URI(XSD.getURI() + "string")),
                  () -> assertThat(parsed.blankNode()).isTrue(),
                  () -> assertThat(parsed.blankNodePredicate()).isEqualTo(new URI("http://example.com#predicate")),
                  () -> assertThat(parsed.uriValue()).isFalse()
                 );
    }

    @Test
    void parse_blankNodeUri_readsPredicateAndUri() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        var predicate = model.createProperty("http://example.com#predicate");
        blank.addProperty(predicate, model.createResource("http://example.com#target"));

        var parsed = ValueNodeParser.parse(blank);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("http://example.com#target"),
                  () -> assertThat(parsed.dataType()).isNull(),
                  () -> assertThat(parsed.blankNode()).isTrue(),
                  () -> assertThat(parsed.blankNodePredicate()).isEqualTo(new URI("http://example.com#predicate")),
                  () -> assertThat(parsed.uriValue()).isTrue()
                 );
    }

    @Test
    void parse_blankNodeWithoutProperties_returnsBlankMetadata() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();

        var parsed = ValueNodeParser.parse(blank);

        assertAll(
                  () -> assertThat(parsed.value()).isNull(),
                  () -> assertThat(parsed.dataType()).isNull(),
                  () -> assertThat(parsed.blankNode()).isTrue(),
                  () -> assertThat(parsed.blankNodePredicate()).isNull(),
                  () -> assertThat(parsed.uriValue()).isFalse()
                 );
    }
}
