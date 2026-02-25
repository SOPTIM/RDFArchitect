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

import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.config.AttributeValueConfig;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class AttributeFixedDefaultResolverTest {

    @Test
    void apply_preservesBlankNodeMetadataAndResolvesDatatype() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var model = ModelFactory.createModelForGraph(graph);
        var attributeUuid = UUID.randomUUID();
        graph.begin(TxnType.WRITE);
        try {
            var attributeResource = model.createResource("http://example.com#Class.attribute");
            attributeResource.addProperty(RDFA.uuid, attributeUuid.toString());

            var fixedBlank = model.createResource();
            fixedBlank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createLiteral("fixedValue"));
            attributeResource.addProperty(CIMS.isFixed, fixedBlank);

            var defaultBlank = model.createResource();
            defaultBlank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createLiteral("defaultValue"));
            attributeResource.addProperty(CIMS.isDefault, defaultBlank);

            var cimAttribute = CIMAttribute.builder()
                                           .uuid(attributeUuid)
                                           .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                      new RDFSLabel("string", "en"),
                                                                      CIMSDataType.Type.PRIMITIVE))
                                           .fixedValue(new CIMSIsFixed("fixedValue"))
                                           .defaultValue(new CIMSIsDefault("defaultValue"))
                                           .build();

            AttributeFixedDefaultResolver.apply(graph, cimAttribute);

            assertAll(
                      () -> assertThat(cimAttribute.getFixedValue().isBlankNode()).isTrue(),
                      () -> assertThat(cimAttribute.getDefaultValue().isBlankNode()).isTrue(),
                      () -> assertThat(cimAttribute.getFixedValue().getDataType()).isEqualTo(new URI(XSD.getURI() + "string")),
                      () -> assertThat(cimAttribute.getDefaultValue().getDataType()).isEqualTo(new URI(XSD.getURI() + "string"))
                     );
            graph.commit();
        } finally {
            graph.end();
        }
    }

    @Test
    void apply_setsBlankNodeForNewValuesWhenConfigured() {
        var config = new AttributeValueConfig();
        config.setNewValuesBlankNode(true);
        try {
            var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
            var cimAttribute = CIMAttribute.builder()
                                           .uuid(UUID.randomUUID())
                                           .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                      new RDFSLabel("string", "en"),
                                                                      CIMSDataType.Type.PRIMITIVE))
                                           .fixedValue(new CIMSIsFixed("fixedValue"))
                                           .defaultValue(new CIMSIsDefault("defaultValue"))
                                           .build();

            graph.begin(TxnType.WRITE);
            try {
                AttributeFixedDefaultResolver.apply(graph, cimAttribute);
                graph.commit();
            } finally {
                graph.end();
            }

            assertAll(
                      () -> assertThat(cimAttribute.getFixedValue().isBlankNode()).isTrue(),
                      () -> assertThat(cimAttribute.getDefaultValue().isBlankNode()).isTrue()
                     );
        } finally {
            config.setNewValuesBlankNode(false);
        }
    }

    @Test
    void apply_rejectsDirectUriValueMetadata() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var model = ModelFactory.createModelForGraph(graph);
        var attributeUuid = UUID.randomUUID();
        graph.begin(TxnType.WRITE);
        try {
            var attributeResource = model.createResource("http://example.com#Class.attribute");
            attributeResource.addProperty(RDFA.uuid, attributeUuid.toString());
            attributeResource.addProperty(CIMS.isFixed, model.createResource("http://example.com#fixedUri"));

            var cimAttribute = CIMAttribute.builder()
                                           .uuid(attributeUuid)
                                           .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                      new RDFSLabel("string", "en"),
                                                                      CIMSDataType.Type.PRIMITIVE))
                                           .fixedValue(new CIMSIsFixed("fixedValue"))
                                           .build();

            assertThatThrownBy(() -> AttributeFixedDefaultResolver.apply(graph, cimAttribute))
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessageContaining("URI resources are not allowed");
        } finally {
            graph.end();
        }
    }

    @Test
    void apply_rejectsBlankNodeWithWrongPredicate() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var model = ModelFactory.createModelForGraph(graph);
        var attributeUuid = UUID.randomUUID();
        graph.begin(TxnType.WRITE);
        try {
            var attributeResource = model.createResource("http://example.com#Class.attribute");
            attributeResource.addProperty(RDFA.uuid, attributeUuid.toString());
            var fixedBlank = model.createResource();
            fixedBlank.addProperty(model.createProperty("http://example.com#predicate"), model.createLiteral("fixedValue"));
            attributeResource.addProperty(CIMS.isFixed, fixedBlank);

            var cimAttribute = CIMAttribute.builder()
                                           .uuid(attributeUuid)
                                           .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                      new RDFSLabel("string", "en"),
                                                                      CIMSDataType.Type.PRIMITIVE))
                                           .fixedValue(new CIMSIsFixed("fixedValue"))
                                           .build();

            assertThatThrownBy(() -> AttributeFixedDefaultResolver.apply(graph, cimAttribute))
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessageContaining("predicate must be rdfs:Literal");
        } finally {
            graph.end();
        }
    }

    @Test
    void apply_rejectsBlankNodeWithNonLiteralObject() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var model = ModelFactory.createModelForGraph(graph);
        var attributeUuid = UUID.randomUUID();
        graph.begin(TxnType.WRITE);
        try {
            var attributeResource = model.createResource("http://example.com#Class.attribute");
            attributeResource.addProperty(RDFA.uuid, attributeUuid.toString());
            var fixedBlank = model.createResource();
            fixedBlank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createResource("http://example.com#fixedUri"));
            attributeResource.addProperty(CIMS.isFixed, fixedBlank);

            var cimAttribute = CIMAttribute.builder()
                                           .uuid(attributeUuid)
                                           .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                      new RDFSLabel("string", "en"),
                                                                      CIMSDataType.Type.PRIMITIVE))
                                           .fixedValue(new CIMSIsFixed("fixedValue"))
                                           .build();

            assertThatThrownBy(() -> AttributeFixedDefaultResolver.apply(graph, cimAttribute))
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessageContaining("object must be a literal");
        } finally {
            graph.end();
        }
    }

    @Test
    void apply_withUnknownDatatypeLabelFallsBackToXsdString() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(UUID.randomUUID())
                                       .dataType(new CIMSDataType(new URI("http://example.com#NotKnownType"),
                                                                  new RDFSLabel("DefinitelyNotADatatype", "en"),
                                                                  CIMSDataType.Type.PRIMITIVE))
                                       .fixedValue(new CIMSIsFixed("fixedValue"))
                                       .build();

        graph.begin(TxnType.WRITE);
        try {
            AttributeFixedDefaultResolver.apply(graph, cimAttribute);
            graph.commit();
        } finally {
            graph.end();
        }

        assertThat(cimAttribute.getFixedValue().getDataType()).isEqualTo(new URI(XSD.getURI() + "string"));
    }

    @Test
    void apply_modelOverloadWorksInsideOpenTransaction() {
        var graph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(UUID.randomUUID())
                                       .dataType(new CIMSDataType(new URI(XSD.getURI() + "string"),
                                                                  new RDFSLabel("string", "en"),
                                                                  CIMSDataType.Type.PRIMITIVE))
                                       .fixedValue(new CIMSIsFixed("fixedValue"))
                                       .defaultValue(new CIMSIsDefault("defaultValue"))
                                       .build();

        graph.begin(TxnType.WRITE);
        try {
            var model = ModelFactory.createModelForGraph(graph);
            assertThatCode(() -> AttributeFixedDefaultResolver.apply(model, cimAttribute)).doesNotThrowAnyException();
            graph.commit();
        } finally {
            graph.end();
        }

        assertAll(
                  () -> assertThat(cimAttribute.getFixedValue().getDataType()).isEqualTo(new URI(XSD.getURI() + "string")),
                  () -> assertThat(cimAttribute.getDefaultValue().getDataType()).isEqualTo(new URI(XSD.getURI() + "string"))
                 );
    }
}
