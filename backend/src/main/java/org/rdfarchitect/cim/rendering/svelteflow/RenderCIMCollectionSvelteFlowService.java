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

package org.rdfarchitect.cim.rendering.svelteflow;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.AttributeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.cim.data.dto.CIMAssociation;
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.CIMCollection;
import org.rdfarchitect.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.cim.rendering.RenderingUtils;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.EnsureDiagramLayoutForCIMCollectionUseCase;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a {@link CIMCollection} to a DTO Record that contains two JSON arrays with nodes and edges used to render a UML diagram using the JavaScript library SvelteFlow.
 */
@RequiredArgsConstructor
public class RenderCIMCollectionSvelteFlowService implements RenderCIMCollectionUseCase {

    //CONSTANTS FOR SVELTEFLOW CUSTOM NODE/EDGE TYPES
    private static final String CLASS_NODE_TYPE = "class";
    private static final String INHERITANCE_EDGE_TYPE = "inheritance";
    private static final String ASSOCIATION_EDGE_TYPE = "association";
    private final FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;
    private final EnsureDiagramLayoutForCIMCollectionUseCase ensureDiagramLayoutForCIMCollectionUseCase;

    @Override
    public RenderingDataDTO renderUML(CIMCollection cimCollection, GraphIdentifier graphIdentifier, UUID packageUUID) {
        ensureDiagramLayoutForCIMCollectionUseCase.ensureDiagramLayoutExists(graphIdentifier, packageUUID, cimCollection);

        //setup
        var uriToUUIDMap = RenderingUtils.createUUIDUriPairs(cimCollection);
        var renderingLayoutData = fetchRenderingLayoutDataUseCase.fetchRenderingLayoutData(graphIdentifier, packageUUID);

        var renderContext = new RenderContext(
                  cimCollection,
                  uriToUUIDMap,
                  renderingLayoutData
        );

        var nodes = assembleNodeDTOList(renderContext);
        var edges = assembleEdgeDTOList(renderContext);

        return SvelteFlowDTO.builder()
                            .nodes(nodes)
                            .edges(edges)
                            .build();
    }

    /**
     * Assembles a list of NodeDTOs from all classes in the collection.
     *
     * @return List of NodeDTOs
     */
    private List<NodeDTO> assembleNodeDTOList(RenderContext renderContext) {
        List<NodeDTO> nodeDTOs = new ArrayList<>();
        for (var cimClassOrEnum : renderContext.cimCollection.getClassesAndEnums()) {
            nodeDTOs.add(assembleNodeDTO(renderContext, cimClassOrEnum));
        }
        return nodeDTOs;
    }

    /**
     * Assembles a NodeDTO for a single CIM class.
     *
     * @param cimClass The CIM class to convert
     *
     * @return NodeDTO containing class data
     */
    private NodeDTO assembleNodeDTO(RenderContext renderContext, CIMClass cimClass) {
        var dop = renderContext.layoutingData().getClassLayoutingData().get(cimClass.getUuid());

        var nodeDTO = NodeDTO.builder()
                             .id(cimClass.getUuid())
                             .type(CLASS_NODE_TYPE);

        var stereotypes = getClassStereotypes(cimClass);
        var attributes = getClassAttributes(renderContext, cimClass);
        var enumEntries = getClassEnumEntries(renderContext, cimClass);

        var positionDTO = PositionDTO.builder()
                                     .x(dop.getPosition().getX())
                                     .y(dop.getPosition().getY())
                                     .build();
        nodeDTO.position(positionDTO);

        var nodeDataDTO = NodeDataDTO.builder()
                                     .label(cimClass.getLabel().getValue())
                                     .belongsToCategory(cimClass.getBelongsToCategory() != null
                                                        ? cimClass.getBelongsToCategory().getLabel().getValue()
                                                        : null)
                                     .stereotypes(stereotypes)
                                     .attributes(attributes)
                                     .enumEntries(enumEntries)
                                     .build();

        nodeDTO.data(nodeDataDTO);

        return nodeDTO.build();
    }

    /**
     * Returns a list of AttributeDTOs for all CIM attributes belonging to a CIM class.
     *
     * @param cimClass The CIM class
     *
     * @return List of AttributeDTOs
     */
    private List<AttributeDTO> getClassAttributes(RenderContext renderContext, CIMClass cimClass) {
        List<AttributeDTO> attributeDTOs = new ArrayList<>();

        for (var cimAttribute : renderContext.cimCollection.getAttributes()) {
            if (!cimAttribute.getDomain().getUri().equals(cimClass.getUri())) {
                continue;
            }
            attributeDTOs.add(AttributeDTO.builder()
                                          .label(cimAttribute.getLabel().getValue())
                                          .type(cimAttribute.getDataType().getLabel().getValue())
                                          .multiplicity(extractMultiplicityString(cimAttribute.getMultiplicity()))
                                          .build());
        }

        return attributeDTOs;
    }

    /**
     * Filters and returns all necessary stereotypes to render for a CIM class
     *
     * @param cimClass The CIM class
     *
     * @return Sorted list of stereotype names
     */
    private List<String> getClassStereotypes(CIMClass cimClass) {
        var stereotypes = cimClass.getStereotypes();
        var stereotypesToRender = new ArrayList<String>();

        if (CollectionUtils.isEmpty(stereotypes) || !stereotypes.contains(new CIMSStereotype(CIMStereotypes.concrete.toString()))) {
            stereotypesToRender.add("abstract");
        }

        for (var stereotype : stereotypes) {
            if (!stereotype.toString().equals(CIMStereotypes.concrete.toString())) {
                String stereotypeToAdd = stereotype.toString();
                if (stereotype.toString().equals(CIMStereotypes.enumeration.toString())) {
                    stereotypeToAdd = CIMStereotypes.enumeration.getLocalName();
                }
                stereotypesToRender.add(stereotypeToAdd);
            }
        }

        stereotypesToRender.sort(String::compareTo);

        return stereotypesToRender;
    }

    /**
     * Returns all enum entries belonging to a CIM class.
     *
     * @param cimClass The CIM class
     *
     * @return List of enum entry labels
     */
    private List<String> getClassEnumEntries(RenderContext renderContext, CIMClass cimClass) {
        List<String> enumEntries = new ArrayList<>();

        for (var cimEnumEntry : renderContext.cimCollection.getEnumEntries()) {
            if (!cimEnumEntry.getType().getUri().equals(cimClass.getUri())) {
                continue;
            }
            enumEntries.add(cimEnumEntry.getLabel().getValue());
        }

        return enumEntries;
    }

    /**
     * Assembles all edges (inheritance and associations) for the diagram.
     *
     * @return List of all EdgeDTOs
     */
    private List<EdgeDTO> assembleEdgeDTOList(RenderContext renderContext) {
        List<EdgeDTO> edgeDTOList = new ArrayList<>();

        var inheritanceEdgeDTOList = assembleInheritanceEdgeDTOList(renderContext);
        var assocationEdgeDTOList = assembleAssociationEdgeDTOList(renderContext);

        edgeDTOList.addAll(inheritanceEdgeDTOList);
        edgeDTOList.addAll(assocationEdgeDTOList);

        return edgeDTOList;
    }

    /**
     * Assembles all inheritance edges from classes with superclasses.
     *
     * @return List of inheritance EdgeDTOs
     */
    private List<EdgeDTO> assembleInheritanceEdgeDTOList(RenderContext renderContext) {
        List<EdgeDTO> inheritanceEdgeDTOList = new ArrayList<>();
        for (var cimClass : renderContext.cimCollection.getClasses()) {
            if (cimClass.getSuperClass() != null) {
                inheritanceEdgeDTOList.add(assembleInheritanceEdgeDTO(renderContext, cimClass));
            }
        }
        return inheritanceEdgeDTOList;
    }

    /**
     * Assembles an inheritance edge from a class to its superclass.
     *
     * @param cimClass The child class
     *
     * @return EdgeDTO representing the inheritance relationship
     */
    private EdgeDTO assembleInheritanceEdgeDTO(RenderContext renderContext, CIMClass cimClass) {
        var classUUID = renderContext.uriToUUIDMap.get(cimClass.getUri().toString());
        var superClassUUID = renderContext.uriToUUIDMap.get(cimClass.getSuperClass().getUri().toString());

        return EdgeDTO.builder()
                      .id(UUID.randomUUID().toString())
                      .type(INHERITANCE_EDGE_TYPE)
                      .source(classUUID)
                      .target(superClassUUID)
                      .data(null)
                      .build();
    }

    /**
     * Assembles all association edges between classes.
     *
     * @return List of association EdgeDTOs
     */
    private List<EdgeDTO> assembleAssociationEdgeDTOList(RenderContext renderContext) {
        List<EdgeDTO> associationEdgeDTOList = new ArrayList<>();
        var handledAssociations = new HashSet<URI>();
        for (var from : renderContext.cimCollection.getAssociations()) {
            var to = renderContext.cimCollection.getAssociations().stream()
                                                .filter(possibleTo -> from.getInverseRoleName().getUri().equals(possibleTo.getUri()))
                                                .findFirst()
                                                .orElse(null);
            if (to == null || (handledAssociations.contains(from.getUri()) && handledAssociations.contains(to.getUri()))) {
                continue;
            }

            associationEdgeDTOList.add(assembleAssociationEdgeDTO(renderContext, from, to));

            handledAssociations.add(from.getUri());
            handledAssociations.add(to.getUri());
        }

        return associationEdgeDTOList;
    }

    /**
     * Assembles an association edge from two paired CIM associations.
     *
     * @param from Source association
     * @param to   Target association
     *
     * @return EdgeDTO with multiplicity and usage data
     */
    private EdgeDTO assembleAssociationEdgeDTO(RenderContext renderContext, CIMAssociation from, CIMAssociation to) {
        var sourceUUID = renderContext.uriToUUIDMap.get(from.getDomain().getUri().toString());
        var targetUUID = renderContext.uriToUUIDMap.get(from.getRange().getUri().toString());

        var fromMultiplicity = extractMultiplicityString(from.getMultiplicity());
        var toMultiplicity = extractMultiplicityString(to.getMultiplicity());
        var useToAssociation = getAssociationUsedValue(from.getAssociationUsed());
        var useFromAssociation = getAssociationUsedValue(to.getAssociationUsed());

        var edgeDataDTO = EdgeDataDTO.builder()
                                     .toMultiplicity(toMultiplicity)
                                     .fromMultiplicity(fromMultiplicity)
                                     .useToAssociation(useToAssociation)
                                     .useFromAssociation(useFromAssociation)
                                     .build();

        return EdgeDTO.builder()
                      .id(UUID.randomUUID().toString())
                      .type(ASSOCIATION_EDGE_TYPE)
                      .source(sourceUUID)
                      .target(targetUUID)
                      .data(edgeDataDTO)
                      .build();
    }

    /**
     * Extracts the multiplicity string from a CIMSMultiplicity.
     *
     * @param multiplicity The multiplicity object
     *
     * @return Multiplicity string without "M:" prefix
     */
    private String extractMultiplicityString(CIMSMultiplicity multiplicity) {
        return multiplicity.getUri().getSuffix().replace("M:", "");
    }

    /**
     * Converts association used value to boolean.
     *
     * @param associationUsed The association used enum
     *
     * @return true if "Yes", false if "No"
     */
    private boolean getAssociationUsedValue(CIMSAssociationUsed associationUsed) {
        var associationUsedValue = associationUsed.toString();
        return switch (associationUsedValue) {
            case "Yes" -> true;
            case "No" -> false;
            default -> throw new IllegalArgumentException(
                      "Unexpected associationUsed value: " + associationUsedValue
            );
        };
    }

    /**
     * Helper record storing the rendering context shared across method calls
     */
    private record RenderContext(CIMCollection cimCollection,
                                 Map<String, UUID> uriToUUIDMap,
                                 RenderingLayoutData layoutingData) {

    }
}
