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

package org.rdfarchitect.cim.rendering.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMAssociationToMermaidBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CIMAssociationToMermaidBuilderTest {

    private static final String URI_PREFIX = "http://example.com#";
    private static final String FROM_CLASS_NAME = "from";
    private static final String TO_CLASS_NAME = "to";

    private static final URI FROM_URI = new URI(URI_PREFIX + FROM_CLASS_NAME + "." + TO_CLASS_NAME);
    private static final URI TO_URI = new URI(URI_PREFIX + TO_CLASS_NAME + "." + FROM_CLASS_NAME);

    private static final RDFSLabel FROM_LABEL = new RDFSLabel(FROM_CLASS_NAME + "." + TO_CLASS_NAME);
    private static final RDFSLabel TO_LABEL = new RDFSLabel(TO_CLASS_NAME + "." + FROM_CLASS_NAME);

    private static final URI FROM_CLASS_URI = new URI(URI_PREFIX + FROM_CLASS_NAME);
    private static final RDFSLabel FROM_CLASS_LABEL = new RDFSLabel(FROM_CLASS_NAME);

    private static final URI TO_CLASS_URI = new URI(URI_PREFIX + TO_CLASS_NAME);
    private static final RDFSLabel TO_CLASS_LABEL = new RDFSLabel(TO_CLASS_NAME);

    public enum AssociationUsed {
        YES,
        NO
    }

    private CIMAssociation from;

    private CIMAssociation to;

    private final Map<String, UUID> uriToUUIDMap = Map.of(
              FROM_URI.toString(), UUID.randomUUID(),
              TO_URI.toString(), UUID.randomUUID(),
              FROM_CLASS_URI.toString(), UUID.randomUUID(),
              TO_CLASS_URI.toString(), UUID.randomUUID()
                                                         );

    public void setUpAssociations(AssociationUsed fromAssociationUsed, String fromMultiplicity, AssociationUsed toAssociationUsed, String toMultiplicity) {

        from = CIMAssociation.builder()
                             .uri(FROM_URI)
                             .label(FROM_LABEL)
                             .domain(new RDFSDomain(FROM_CLASS_URI, FROM_CLASS_LABEL))
                             .range(new RDFSRange(TO_CLASS_URI, TO_CLASS_LABEL))
                             .inverseRoleName(new CIMSInverseRoleName(TO_URI))
                             .associationUsed(new CIMSAssociationUsed(fromAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                             .multiplicity(new CIMSMultiplicity(URI_PREFIX + fromMultiplicity))
                             .build();

        to = CIMAssociation.builder()
                           .uri(TO_URI)
                           .label(TO_LABEL)
                           .domain(new RDFSDomain(TO_CLASS_URI, TO_CLASS_LABEL))
                           .range(new RDFSRange(FROM_CLASS_URI, FROM_CLASS_LABEL))
                           .inverseRoleName(new CIMSInverseRoleName(FROM_URI))
                           .associationUsed(new CIMSAssociationUsed(toAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                           .multiplicity(new CIMSMultiplicity(URI_PREFIX + toMultiplicity))
                           .build();
    }

    @Test
    void build_biDirectionalAssociation_returnsBidirectionalMermaidAssociation() {
        //Arrange
        setUpAssociations(AssociationUsed.YES, "M:1", AssociationUsed.YES, "M:1");
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act
        var result = cimAssociationMermaidBuilder.build().toString();
        //Assert
        assertThat(result).isEqualTo(
                  "`" + uriToUUIDMap.get(FROM_CLASS_URI.toString()) + "` \"M:1\" <--> \"M:1\" `" + uriToUUIDMap.get(TO_CLASS_URI.toString()) + "`\n"
                                    );
    }

    @Test
    void build_uniDirectionalAssociation_returnsUnidirectionalMermaidAssociation() {
        //Arrange
        setUpAssociations(AssociationUsed.YES, "M:1", AssociationUsed.NO, "M:1");
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act
        var result = cimAssociationMermaidBuilder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  "`" + uriToUUIDMap.get(FROM_CLASS_URI.toString()) + "` \"M:1\" --> \"M:1\" `" + uriToUUIDMap.get(TO_CLASS_URI.toString()) + "`\n"
                                    );
    }

    @Test
    void build_reverseUniDirectionalAssociation_returnsReverseUniDirectionalMermaidAssociation() {
        //Arrange
        setUpAssociations(AssociationUsed.NO, "M:1", AssociationUsed.YES, "M:1");
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act
        var result = cimAssociationMermaidBuilder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  "`" + uriToUUIDMap.get(FROM_CLASS_URI.toString()) + "` \"M:1\" <-- \"M:1\" `" + uriToUUIDMap.get(TO_CLASS_URI.toString()) + "`\n"
                                    );
    }

    @Test
    void build_noDirectionalAssociation_returnsNoDirectionalMermaidAssociation() {
        //Arrange
        setUpAssociations(AssociationUsed.NO, "M:1", AssociationUsed.NO, "M:1");
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act
        var result = cimAssociationMermaidBuilder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  "`" + uriToUUIDMap.get(FROM_CLASS_URI.toString()) + "` \"M:1\" -- \"M:1\" `" + uriToUUIDMap.get(TO_CLASS_URI.toString()) + "`\n"
                                    );
    }

    @ParameterizedTest
    @ValueSource(strings = {"M:1", "M:2", "M:0..n", "M:m..n"})
    void build_associationWithDifferentMultiplicities_returnsMermaidAssociationWithCorrectMultiplicities(String fromMultiplicity) {
        //Arrange
        setUpAssociations(AssociationUsed.YES, fromMultiplicity, AssociationUsed.YES, fromMultiplicity);
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act
        var result = cimAssociationMermaidBuilder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  "`" + uriToUUIDMap.get(FROM_CLASS_URI.toString()) + "` \"" + fromMultiplicity + "\" <--> \"" + fromMultiplicity + "\" `" + uriToUUIDMap.get(TO_CLASS_URI.toString()) + "`\n"
                                    );
    }

    @Test
    void build_associationWithNullMultiplicity_throwsException() {
        //Arrange
        setUpAssociations(AssociationUsed.YES, "", AssociationUsed.YES, "");
        from.setMultiplicity(null);
        to.setMultiplicity(null);
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act/Assert
        assertThatException().isThrownBy(cimAssociationMermaidBuilder::build);
    }

    @Test
    void build_associationWithNullAssociationUsed_throwsException() {
        //Arrange
        setUpAssociations(AssociationUsed.YES, "", AssociationUsed.YES, "");
        from.setAssociationUsed(null);
        to.setAssociationUsed(null);
        var cimAssociationMermaidBuilder = new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap);

        //Act/Assert
        assertThatException().isThrownBy(cimAssociationMermaidBuilder::build);
    }
}
