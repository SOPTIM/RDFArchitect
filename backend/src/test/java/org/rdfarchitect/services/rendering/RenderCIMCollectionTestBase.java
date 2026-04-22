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

package org.rdfarchitect.services.rendering;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rendering.mermaid.RenderCIMCollectionMermaidService;
import org.rdfarchitect.models.cim.rendering.svelteflow.RenderCIMCollectionSvelteFlowService;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.EnsureDiagramLayoutForCIMCollectionUseCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class RenderCIMCollectionTestBase {

    protected static final String URI_PREFIX = "http://example.com#";
    protected static CIMCollection cimCollection;
    protected static RenderCIMCollectionMermaidService mermaidRenderer;
    protected static RenderCIMCollectionSvelteFlowService svelteFlowRenderer;

    @BeforeAll
    static void setUpEnvironment() {
        //das anfragen der layout informationen wird weg gemocked
        FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase = mock(FetchRenderingLayoutDataUseCase.class);
        EnsureDiagramLayoutForCIMCollectionUseCase ensureDiagramLayoutForCIMCollectionUseCase = mock(EnsureDiagramLayoutForCIMCollectionUseCase.class);

        var mockXYPosition = mock(XYZPosition.class);
        when(mockXYPosition.getX()).thenReturn(0f);
        when(mockXYPosition.getY()).thenReturn(0f);

        var mockDop = mock(DiagramObjectPoint.class);
        when(mockDop.getPosition()).thenReturn(mockXYPosition);

        var mockMap = mock(Map.class);
        when(mockMap.get(any(UUID.class))).thenReturn(mockDop);

        var mockLayoutData = RenderingLayoutData.builder()
                                                .classLayoutingData(mockMap)
                                                .build();
        when(fetchRenderingLayoutDataUseCase.fetchRenderingLayoutData(any(), any()))
                  .thenReturn(mockLayoutData);

        mermaidRenderer = new RenderCIMCollectionMermaidService();
        svelteFlowRenderer = new RenderCIMCollectionSvelteFlowService(fetchRenderingLayoutDataUseCase, ensureDiagramLayoutForCIMCollectionUseCase);
    }

    @BeforeEach
    void resetEnvironment() {
        cimCollection = new CIMCollection();
    }

    protected void addPackage(String packageLabel) {
        var uri = new URI(URI_PREFIX + packageLabel);
        var label = new RDFSLabel(packageLabel);

        var cimPackage = CIMPackage.builder()
                                   .uuid(UUID.randomUUID())
                                   .uri(uri)
                                   .label(label)
                                   .build();
        cimCollection.getPackages().add(cimPackage);
    }

    protected void addClass(String packageLabel, String classLabel) {
        var uri = new URI(URI_PREFIX + classLabel);
        var label = new RDFSLabel(classLabel);

        var cimClass = CIMClass.builder()
                               .uuid(UUID.randomUUID())
                               .uri(uri)
                               .label(label)
                               .superClass(null)
                               .belongsToCategory(null);
        if (packageLabel != null) {
            cimClass.belongsToCategory(new CIMSBelongsToCategory(new URI(URI_PREFIX + packageLabel), new RDFSLabel(packageLabel), UUID.randomUUID()));
        }
        cimCollection.getClasses().add(cimClass.build());
    }

    protected void addAttribute(String classLabel, String attributeLabel, XSDDatatype datatype) {
        var uri = new URI(URI_PREFIX + classLabel + "." + attributeLabel);
        var label = new RDFSLabel(attributeLabel);

        var dataTypeUri = new URI(datatype.getURI());
        var dataType = new CIMSPrimitiveDataType(dataTypeUri, new RDFSLabel(dataTypeUri.getSuffix()));

        var attribute = CIMAttribute.builder()
                                    .uuid(UUID.randomUUID())
                                    .uri(uri)
                                    .label(label)
                                    .dataType(dataType)
                                    .domain(new RDFSDomain(new URI(URI_PREFIX + classLabel), new RDFSLabel(classLabel)))
                                    .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                                    .stereotype(new CIMSStereotype(CIMStereotypes.attribute.getURI()))
                                    .build();

        cimCollection.getAttributes().add(attribute);
    }

    protected void addAssociation(String domainLabel, String rangeLabel, AssociationUsed fromAssociationUsed, AssociationUsed toAssociationUsed) {
        var fromUri = new URI(URI_PREFIX + domainLabel + "." + rangeLabel);
        var toUri = new URI(URI_PREFIX + rangeLabel + "." + domainLabel);
        var fromLabel = new RDFSLabel(fromUri.getSuffix());
        var toLabel = new RDFSLabel(toUri.getSuffix());

        var domainUri = new URI(URI_PREFIX + domainLabel);
        var domainRDFSLabel = new RDFSLabel(domainLabel);

        var rangeUri = new URI(URI_PREFIX + rangeLabel);
        var rangeRDFSLabel = new RDFSLabel(rangeLabel);

        var from = CIMAssociation.builder()
                                 .uuid(UUID.randomUUID())
                                 .uri(fromUri)
                                 .label(fromLabel)
                                 .domain(new RDFSDomain(domainUri, domainRDFSLabel))
                                 .range(new RDFSRange(rangeUri, rangeRDFSLabel))
                                 .inverseRoleName(new CIMSInverseRoleName(toUri))
                                 .associationUsed(new CIMSAssociationUsed(fromAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                                 .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                                 .build();

        var to = CIMAssociation.builder()
                               .uuid(UUID.randomUUID())
                               .uri(toUri)
                               .label(toLabel)
                               .domain(new RDFSDomain(rangeUri, rangeRDFSLabel))
                               .range(new RDFSRange(domainUri, domainRDFSLabel))
                               .inverseRoleName(new CIMSInverseRoleName(fromUri))
                               .associationUsed(new CIMSAssociationUsed(toAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                               .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                               .build();

        cimCollection.getAssociations().add(from);
        cimCollection.getAssociations().add(to);
    }

    protected void addEnum(String packageLabel, String enumLabel) {
        var uri = new URI(URI_PREFIX + enumLabel);
        var label = new RDFSLabel(enumLabel);

        var cimEnum = CIMClass.builder()
                              .uuid(UUID.randomUUID())
                              .uri(uri)
                              .label(label)
                              .belongsToCategory(null);

        if (packageLabel != null) {
            cimEnum.belongsToCategory(new CIMSBelongsToCategory(new URI(URI_PREFIX + packageLabel), new RDFSLabel(packageLabel), UUID.randomUUID()));
        }
        cimEnum.stereotypes(new ArrayList<>(List.of(new CIMSStereotype(CIMStereotypes.enumeration.getURI()))));
        cimCollection.getEnums().add(cimEnum.build());
    }

    protected void addEnumEntry(String enumLabel, String enumEntryLabel) {
        var enumEntry = CIMEnumEntry.builder()
                                    .uuid(UUID.randomUUID())
                                    .uri(new URI(URI_PREFIX + enumEntryLabel))
                                    .label(new RDFSLabel(enumEntryLabel))
                                    .type(new RDFType(new URI(URI_PREFIX + enumLabel), new RDFSLabel(enumLabel)))
                                    .build();

        cimCollection.getEnumEntries().add(enumEntry);
    }

    protected enum AssociationUsed {
        YES,
        NO
    }
}
