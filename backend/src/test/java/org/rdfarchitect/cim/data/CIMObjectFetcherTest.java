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

package org.rdfarchitect.cim.data;

import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.RDFA;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class CIMObjectFetcherTest {

    @Test
    void fetchCIMAttributeList_parsesBlankNodeFixedDefaultFromGraphModel() {
        var model = ModelFactory.createDefaultModel();
        var domainUuid = UUID.randomUUID();
        var attributeUuid = UUID.randomUUID();

        var domain = model.createResource("http://example.com#Domain");
        domain.addProperty(RDFA.uuid, domainUuid.toString());
        domain.addProperty(RDFS.label, "Domain");

        var attribute = model.createResource("http://example.com#Domain.attribute");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, attributeUuid.toString());
        attribute.addProperty(RDFS.label, "attribute");
        attribute.addProperty(CIMS.multiplicity, model.createResource("http://example.com#multiplicity"));
        attribute.addProperty(RDFS.domain, domain);

        var fixedBlank = model.createResource();
        fixedBlank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createTypedLiteral("42", XSD.integer.getURI()));
        attribute.addProperty(CIMS.isFixed, fixedBlank);

        var defaultBlank = model.createResource();
        defaultBlank.addProperty(model.createProperty(RDFS.Literal.getURI()), model.createTypedLiteral("true", XSD.xboolean.getURI()));
        attribute.addProperty(CIMS.isDefault, defaultBlank);

        var query = QueryFactory.create("""
                                         SELECT ?uri ?uuid ?label ?multiplicity ?domainURI ?domainLabel ?domainUUID ?isFixed ?isDefault WHERE {
                                           ?uri <%s> <%s> .
                                           ?uri <%s> ?uuid .
                                           ?uri <%s> ?label .
                                           ?uri <%s> ?multiplicity .
                                           ?uri <%s> ?domainURI .
                                           ?domainURI <%s> ?domainLabel .
                                           ?domainURI <%s> ?domainUUID .
                                           OPTIONAL { ?uri <%s> ?isFixed . }
                                           OPTIONAL { ?uri <%s> ?isDefault . }
                                         }
                                         """
                                                 .formatted(
                                                         RDF.type.getURI(),
                                                         RDF.Property.getURI(),
                                                         RDFA.uuid.getURI(),
                                                         RDFS.label.getURI(),
                                                         CIMS.multiplicity.getURI(),
                                                         RDFS.domain.getURI(),
                                                         RDFS.label.getURI(),
                                                         RDFA.uuid.getURI(),
                                                         CIMS.isFixed.getURI(),
                                                         CIMS.isDefault.getURI()
                                                 ));

        var fetcher = new CIMObjectFetcher(model.getGraph(), "default", PrefixMapping.Factory.create());
        var attributes = fetcher.fetchCIMAttributeList(query);

        assertThat(attributes).hasSize(1);
        var parsedAttribute = attributes.getFirst();
        assertAll(
                  () -> assertThat(parsedAttribute.getFixedValue()).isNotNull(),
                  () -> assertThat(parsedAttribute.getFixedValue().isBlankNode()).isTrue(),
                  () -> assertThat(parsedAttribute.getFixedValue().getValue()).isEqualTo("42"),
                  () -> assertThat(parsedAttribute.getFixedValue().getDataType()).isEqualTo(new URI(XSD.integer.getURI())),
                  () -> assertThat(parsedAttribute.getDefaultValue()).isNotNull(),
                  () -> assertThat(parsedAttribute.getDefaultValue().isBlankNode()).isTrue(),
                  () -> assertThat(parsedAttribute.getDefaultValue().getValue()).isEqualTo("true"),
                  () -> assertThat(parsedAttribute.getDefaultValue().getDataType()).isEqualTo(new URI(XSD.xboolean.getURI()))
                 );
    }
}
