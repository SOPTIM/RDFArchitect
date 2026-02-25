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
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ValueNodeParserTest {

    @Test
    void parse_literalNode_readsValueAndDatatype() {
        Model model = ModelFactory.createDefaultModel();
        var literal = model.createTypedLiteral("42", XSD.integer.getURI());

        var parsed = ValueNodeParser.parse(literal);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("42"),
                  () -> assertThat(parsed.dataType()).isEqualTo(new URI(XSD.integer.getURI())),
                  () -> assertThat(parsed.blankNode()).isFalse()
                 );
    }

    @Test
    void parse_blankNodeLiteral_readsLiteralValueWithBlankNodeMarker() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        blank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createLiteral("blank"));

        var parsed = ValueNodeParser.parse(blank);

        assertAll(
                  () -> assertThat(parsed.value()).isEqualTo("blank"),
                  () -> assertThat(parsed.dataType()).isEqualTo(new URI(XSD.xstring.getURI())),
                  () -> assertThat(parsed.blankNode()).isTrue()
                 );
    }

    @Test
    void parse_uriNode_throwsException() {
        Model model = ModelFactory.createDefaultModel();
        var resource = model.createResource("http://example.com#value");

        assertThatThrownBy(() -> ValueNodeParser.parse(resource))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("URI resources are not allowed");
    }

    @Test
    void parse_blankNodeWithWrongPredicate_throwsException() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        blank.addProperty(model.createProperty("http://example.com#predicate"), model.createLiteral("value"));

        assertThatThrownBy(() -> ValueNodeParser.parse(blank))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("predicate must be rdfs:Literal");
    }

    @Test
    void parse_blankNodeWithUriObject_throwsException() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        blank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createResource("http://example.com#target"));

        assertThatThrownBy(() -> ValueNodeParser.parse(blank))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("object must be a literal");
    }

    @Test
    void parse_blankNodeWithoutProperties_throwsException() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();

        assertThatThrownBy(() -> ValueNodeParser.parse(blank))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("must contain exactly one rdfs:Literal statement");
    }

    @Test
    void parse_blankNodeWithMultipleProperties_throwsException() {
        Model model = ModelFactory.createDefaultModel();
        var blank = model.createResource();
        blank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createLiteral("value1"));
        blank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createLiteral("value2"));

        assertThatThrownBy(() -> ValueNodeParser.parse(blank))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("must contain exactly one statement");
    }
}
