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

package org.rdfarchitect.models.changes.semanticchanges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

class SemanticAttributeChangeTest {

    private static final String PREFIX = "http://example.org#";

    /**
     * {@code CrossDomainPropertyPropagationHandler} and {@code InheritanceChangeHandler} both build
     * propagated attribute entries through this constructor - it must leave {@code allowedValues}
     * non-null like every other constructor on this class, or copying the attribute later (which
     * every migration report and context snapshot does) throws a NullPointerException out of {@code
     * new ArrayList<>(null)}.
     */
    @Test
    void constructedFromResourceAndChangeType_canBeCopiedWithoutThrowing() {
        var model = ModelFactory.createDefaultModel();
        var attribute = model.createResource(PREFIX + "voltage");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral("voltage"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

        var attributeChange =
                new SemanticAttributeChange(
                        attribute, SemanticResourceChangeType.ADDED_FROM_INHERITANCE);

        assertThat(attributeChange.getAllowedValues()).isNotNull();
        assertThatCode(attributeChange::copy).doesNotThrowAnyException();
    }
}
