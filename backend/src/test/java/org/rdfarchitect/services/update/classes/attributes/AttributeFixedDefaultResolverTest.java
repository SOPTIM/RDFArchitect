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

package org.rdfarchitect.services.update.classes.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.GraphMemFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.UUID;

class AttributeFixedDefaultResolverTest {

    private static final UUID ATTRIBUTE_UUID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ATTRIBUTE_URI = "http://example.com#TestAttribute";

    @Test
    @DisplayName("does nothing when attribute has no fixed/default value")
    void resolve_noFixedOrDefault_doesNothing() {
        var resolver = new AttributeFixedDefaultResolver(false);
        var attribute = baseAttribute().build();

        resolver.resolve(emptyGraph(), attribute);

        assertThat(attribute.getFixedValue()).isNull();
        assertThat(attribute.getDefaultValue()).isNull();
    }

    @Test
    @DisplayName("with newValuesBlankNode=false and no existing graph match, blankNode stays false")
    void resolve_newValueWithLiteralDefault_blankNodeStaysFalse() {
        var resolver = new AttributeFixedDefaultResolver(false);
        var attribute =
                baseAttribute()
                        .fixedValue(new CIMSIsFixed("fixed"))
                        .defaultValue(new CIMSIsDefault("default"))
                        .build();

        resolver.resolve(emptyGraph(), attribute);

        assertThat(attribute.getFixedValue().isBlankNode()).isFalse();
        assertThat(attribute.getDefaultValue().isBlankNode()).isFalse();
        assertThat(attribute.getFixedValue().getDataType())
                .isEqualTo(new URI(XSD.xstring.getURI()));
        assertThat(attribute.getDefaultValue().getDataType())
                .isEqualTo(new URI(XSD.xstring.getURI()));
    }

    @Test
    @DisplayName("with newValuesBlankNode=true and no existing graph match, blankNode is true")
    void resolve_newValueWithBlankNodeDefault_blankNodeBecomesTrue() {
        var resolver = new AttributeFixedDefaultResolver(true);
        var attribute =
                baseAttribute()
                        .fixedValue(new CIMSIsFixed("fixed"))
                        .defaultValue(new CIMSIsDefault("default"))
                        .build();

        resolver.resolve(emptyGraph(), attribute);

        assertThat(attribute.getFixedValue().isBlankNode()).isTrue();
        assertThat(attribute.getDefaultValue().isBlankNode()).isTrue();
    }

    @Test
    @DisplayName("existing blank-node fixed value preserves blankNode=true regardless of config")
    void resolve_existingBlankNode_preservesBlankNodeTrue() {
        var resolver = new AttributeFixedDefaultResolver(false);
        var attribute = baseAttribute().fixedValue(new CIMSIsFixed("new")).build();

        resolver.resolve(graphWithBlankNodeFixedValue(), attribute);

        assertThat(attribute.getFixedValue().isBlankNode()).isTrue();
    }

    @Test
    @DisplayName("existing literal fixed value preserves blankNode=false regardless of config")
    void resolve_existingLiteral_preservesBlankNodeFalse() {
        var resolver = new AttributeFixedDefaultResolver(true);
        var attribute = baseAttribute().fixedValue(new CIMSIsFixed("new")).build();

        resolver.resolve(graphWithLiteralFixedValue(), attribute);

        assertThat(attribute.getFixedValue().isBlankNode()).isFalse();
    }

    private static CIMAttribute.CIMAttributeBuilder baseAttribute() {
        return CIMAttribute.builder()
                .uuid(ATTRIBUTE_UUID)
                .uri(new URI(ATTRIBUTE_URI))
                .label(new RDFSLabel("test", "en"));
    }

    private static GraphRewindableWithUUIDs emptyGraph() {
        return new GraphRewindableWithUUIDs(GraphMemFactory.createDefaultGraph(), 10, 5);
    }

    private static GraphRewindableWithUUIDs graphWithBlankNodeFixedValue() {
        var base = GraphMemFactory.createDefaultGraph();
        var model = ModelFactory.createModelForGraph(base);
        var attribute = model.createResource(ATTRIBUTE_URI);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, model.createLiteral(ATTRIBUTE_UUID.toString()));
        var blank = model.createResource();
        blank.addProperty(
                ResourceFactory.createProperty(RDFS.Literal.getURI()),
                ResourceFactory.createPlainLiteral("existing"));
        attribute.addProperty(CIMS.isFixed, blank);
        return new GraphRewindableWithUUIDs(base, 10, 5);
    }

    private static GraphRewindableWithUUIDs graphWithLiteralFixedValue() {
        var base = GraphMemFactory.createDefaultGraph();
        var model = ModelFactory.createModelForGraph(base);
        var attribute = model.createResource(ATTRIBUTE_URI);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, model.createLiteral(ATTRIBUTE_UUID.toString()));
        attribute.addProperty(CIMS.isFixed, ResourceFactory.createPlainLiteral("existing"));
        return new GraphRewindableWithUUIDs(base, 10, 5);
    }
}
