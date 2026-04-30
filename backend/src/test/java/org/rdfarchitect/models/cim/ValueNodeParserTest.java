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

package org.rdfarchitect.models.cim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

class ValueNodeParserTest {

    private static final Property RDFS_LITERAL =
            ResourceFactory.createProperty(RDFS.Literal.getURI());

    @Nested
    @DisplayName("parse direct literals")
    class ParseDirectLiterals {

        @Test
        @DisplayName("string literal returns xsd:string datatype, blankNode=false")
        void parse_stringLiteral_returnsXsdStringDataType() {
            // RDF 1.1: a string literal is implicitly typed as xsd:string.
            var literal = ModelFactory.createDefaultModel().createLiteral("hello");

            var parsed = ValueNodeParser.parse(literal);

            assertThat(parsed.value()).isEqualTo("hello");
            assertThat(parsed.dataType()).isEqualTo(new URI(XSDDatatype.XSDstring.getURI()));
            assertThat(parsed.blankNode()).isFalse();
        }

        @Test
        @DisplayName("typed literal returns datatype URI, blankNode=false")
        void parse_typedLiteral_returnsDataType() {
            var literal =
                    ModelFactory.createDefaultModel()
                            .createTypedLiteral("42", XSDDatatype.XSDinteger);

            var parsed = ValueNodeParser.parse(literal);

            assertThat(parsed.value()).isEqualTo("42");
            assertThat(parsed.dataType()).isEqualTo(new URI(XSDDatatype.XSDinteger.getURI()));
            assertThat(parsed.blankNode()).isFalse();
        }
    }

    @Nested
    @DisplayName("parse blank-node value wrappers")
    class ParseBlankNodes {

        @Test
        @DisplayName("valid blank node with rdfs:Literal predicate returns blankNode=true")
        void parse_validBlankNode_returnsBlankNodeTrue() {
            var model = ModelFactory.createDefaultModel();
            var blank = model.createResource();
            blank.addProperty(
                    RDFS_LITERAL, model.createTypedLiteral("default", XSDDatatype.XSDstring));

            var parsed = ValueNodeParser.parse(blank, model);

            assertThat(parsed.value()).isEqualTo("default");
            assertThat(parsed.dataType()).isEqualTo(new URI(XSDDatatype.XSDstring.getURI()));
            assertThat(parsed.blankNode()).isTrue();
        }

        @Test
        @DisplayName("blank node with predicate other than rdfs:Literal is rejected")
        void parse_blankNodeWithWrongPredicate_throws() {
            var model = ModelFactory.createDefaultModel();
            var blank = model.createResource();
            blank.addProperty(RDFS.label, model.createLiteral("x"));

            assertThatThrownBy(() -> ValueNodeParser.parse(blank, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("predicate must be rdfs:Literal");
        }

        @Test
        @DisplayName("blank node with non-literal object is rejected")
        void parse_blankNodeWithNonLiteralObject_throws() {
            var model = ModelFactory.createDefaultModel();
            var blank = model.createResource();
            blank.addProperty(RDFS_LITERAL, model.createResource("http://example.com#R"));

            assertThatThrownBy(() -> ValueNodeParser.parse(blank, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be a literal");
        }

        @Test
        @DisplayName("blank node with more than one statement is rejected")
        void parse_blankNodeWithMultipleStatements_throws() {
            var model = ModelFactory.createDefaultModel();
            var blank = model.createResource();
            blank.addProperty(RDFS_LITERAL, model.createLiteral("a"));
            blank.addProperty(RDFS.label, model.createLiteral("b"));

            assertThatThrownBy(() -> ValueNodeParser.parse(blank, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one");
        }

        @Test
        @DisplayName("empty blank node is rejected")
        void parse_emptyBlankNode_throws() {
            var model = ModelFactory.createDefaultModel();
            var blank = model.createResource();

            assertThatThrownBy(() -> ValueNodeParser.parse(blank, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one rdfs:Literal");
        }
    }

    @Nested
    @DisplayName("parse invalid input")
    class ParseInvalid {

        @Test
        @DisplayName("URI resource is rejected")
        void parse_uriResource_throws() {
            var resource = ResourceFactory.createResource("http://example.com#R");

            assertThatThrownBy(() -> ValueNodeParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("URI resources are not allowed");
        }

        @Test
        @DisplayName("null node is rejected")
        void parse_null_throws() {
            assertThatThrownBy(() -> ValueNodeParser.parse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value node must not be null");
        }
    }
}
