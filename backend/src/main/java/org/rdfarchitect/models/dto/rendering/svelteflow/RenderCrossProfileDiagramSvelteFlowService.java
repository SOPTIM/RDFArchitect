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

package org.rdfarchitect.models.dto.rendering.svelteflow;

import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.GraphSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.MergedClassDTO;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.AttributeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EnumEntryDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.dto.rendering.RenderCrossProfileDiagramUseCase;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RenderCrossProfileDiagramSvelteFlowService
        implements RenderCrossProfileDiagramUseCase {

    private final FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;

    public RenderCrossProfileDiagramSvelteFlowService(
            FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase) {
        this.fetchRenderingLayoutDataUseCase = fetchRenderingLayoutDataUseCase;
    }

    // CONSTANTS FOR SVELTEFLOW CUSTOM NODE/EDGE TYPES
    private static final String CLASS_NODE_TYPE = "class";
    private static final String INHERITANCE_EDGE_TYPE = "inheritance";
    private static final String ASSOCIATION_EDGE_TYPE = "association";

    @Override
    public RenderingDataDTO renderCrossProfileDiagramUML(
            CrossProfileDiagramDTO diagram, String datasetName) {
        if (CollectionUtils.isEmpty(diagram.getClasses())) {
            return SvelteFlowDTO.builder().nodes(List.of()).edges(List.of()).build();
        }

        var layoutData =
                fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(
                        datasetName, diagram.getDiagramId());

        Map<String, UUID> classUriToUUIDMap = new HashMap<>();
        for (var mergedClass : diagram.getClasses()) {
            classUriToUUIDMap.put(mergedClass.getClassUri(), mergedClass.getUuid());
        }

        var nodes = assembleNodeDTOList(diagram, layoutData);
        var edges = assembleEdgeDTOList(diagram, classUriToUUIDMap);

        return SvelteFlowDTO.builder().nodes(nodes).edges(edges).build();
    }

    /** Assembles a list of NodeDTOs from all MergedClassDTOs in the diagram. */
    private List<NodeDTO> assembleNodeDTOList(
            CrossProfileDiagramDTO diagram, RenderingLayoutData layoutData) {
        List<NodeDTO> nodeDTOs = new ArrayList<>();
        for (var mergedClass : diagram.getClasses()) {
            nodeDTOs.add(assembleNodeDTO(mergedClass, layoutData));
        }
        return nodeDTOs;
    }

    /** Assembles a NodeDTO for a single MergedClassDTO. */
    private NodeDTO assembleNodeDTO(MergedClassDTO mergedClass, RenderingLayoutData layoutData) {
        var dop =
                layoutData != null && layoutData.getClassLayoutingData() != null
                        ? layoutData.getClassLayoutingData().get(mergedClass.getUuid())
                        : null;

        var positionDTO =
                dop != null
                        ? PositionDTO.builder()
                                .x(dop.getPosition().getX())
                                .y(dop.getPosition().getY())
                                .z(dop.getPosition().getZ())
                                .build()
                        : PositionDTO.builder().x(0).y(0).z(0).build();

        var stereotypes = getClassStereotypes(mergedClass);
        var attributes = getClassAttributes(mergedClass);
        var enumEntries = getClassEnumEntries(mergedClass);

        String graphUri =
                !CollectionUtils.isEmpty(mergedClass.getSources())
                        ? mergedClass.getSources().get(0).getGraphUri()
                        : null;

        var nodeDataDTO =
                NodeDataDTO.builder()
                        .label(mergedClass.getLabel())
                        .graphUri(graphUri)
                        .belongsToCategory(null)
                        .stereotypes(stereotypes)
                        .attributes(attributes)
                        .enumEntries(enumEntries)
                        .build();

        return NodeDTO.builder()
                .id(mergedClass.getUuid())
                .type(CLASS_NODE_TYPE)
                .position(positionDTO)
                .data(nodeDataDTO)
                .build();
    }

    /** Filters and returns all necessary stereotypes to render for a MergedClassDTO. */
    private List<String> getClassStereotypes(MergedClassDTO mergedClass) {
        var stereotypes = mergedClass.getStereotypes();
        var stereotypesToRender = new ArrayList<String>();

        boolean isConcrete =
                !CollectionUtils.isEmpty(stereotypes)
                        && stereotypes.stream()
                                .anyMatch(
                                        s ->
                                                s.toString()
                                                        .equals(
                                                                CIMStereotypes.concrete
                                                                        .toString()));

        if (!isConcrete) {
            stereotypesToRender.add("abstract");
        }

        if (!CollectionUtils.isEmpty(stereotypes)) {
            for (var stereotype : stereotypes) {
                if (!stereotype.toString().equals(CIMStereotypes.concrete.toString())) {
                    String stereotypeToAdd = stereotype.toString();
                    if (stereotype.toString().equals(CIMStereotypes.enumeration.toString())) {
                        stereotypeToAdd = CIMStereotypes.enumeration.getLocalName();
                    }
                    stereotypesToRender.add(stereotypeToAdd);
                }
            }
        }

        stereotypesToRender.sort(String::compareTo);
        return stereotypesToRender;
    }

    /** Returns a list of AttributeDTOs from the GraphSourced attributes of a MergedClassDTO. */
    private List<AttributeDTO> getClassAttributes(MergedClassDTO mergedClass) {
        List<AttributeDTO> attributeDTOs = new ArrayList<>();
        if (CollectionUtils.isEmpty(mergedClass.getAttributes())) {
            return attributeDTOs;
        }
        for (GraphSourceDTO<org.rdfarchitect.api.dto.attributes.AttributeDTO> graphSourced :
                mergedClass.getAttributes()) {
            var attr = graphSourced.getValue();
            attributeDTOs.add(
                    AttributeDTO.builder()
                            .label(attr.getLabel())
                            .type(attr.getDataType() != null ? attr.getDataType().getLabel() : null)
                            .multiplicity(attr.getMultiplicity())
                            .graphUri(graphSourced.getGraphUri())
                            .color(graphSourced.getGraphColor())
                            .build());
        }
        return attributeDTOs;
    }

    /** Returns a list of EnumEntryDTOs from the GraphSourced enum entries of a MergedClassDTO. */
    private List<EnumEntryDTO> getClassEnumEntries(MergedClassDTO mergedClass) {
        List<EnumEntryDTO> enumEntries = new ArrayList<>();
        if (CollectionUtils.isEmpty(mergedClass.getEnumEntries())) {
            return enumEntries;
        }
        for (GraphSourceDTO<org.rdfarchitect.api.dto.enumentries.EnumEntryDTO> graphSourced :
                mergedClass.getEnumEntries()) {
            var entry = graphSourced.getValue();
            enumEntries.add(
                    EnumEntryDTO.builder()
                            .label(entry.getLabel())
                            .graphUri(graphSourced.getGraphUri())
                            .color(graphSourced.getGraphColor())
                            .build());
        }
        return enumEntries;
    }

    /** Assembles all edges (inheritance and associations) for the diagram. */
    private List<EdgeDTO> assembleEdgeDTOList(
            CrossProfileDiagramDTO diagram, Map<String, UUID> classUriToUUIDMap) {
        List<EdgeDTO> edges = new ArrayList<>();
        edges.addAll(assembleInheritanceEdgeDTOList(diagram, classUriToUUIDMap));
        edges.addAll(assembleAssociationEdgeDTOList(diagram, classUriToUUIDMap));
        return edges;
    }

    /** Assembles inheritance edges from the superClasses of each MergedClassDTO. */
    private List<EdgeDTO> assembleInheritanceEdgeDTOList(
            CrossProfileDiagramDTO diagram, Map<String, UUID> classUriToUUIDMap) {
        List<EdgeDTO> inheritanceEdges = new ArrayList<>();
        for (var mergedClass : diagram.getClasses()) {
            if (CollectionUtils.isEmpty(mergedClass.getSuperClasses())) {
                continue;
            }
            for (var graphSourcedSuperClass : mergedClass.getSuperClasses()) {
                var superClass = graphSourcedSuperClass.getValue();
                var superClassUUID =
                        classUriToUUIDMap.entrySet().stream()
                                .filter(
                                        e -> {
                                            String uri = e.getKey();
                                            String label = superClass.getLabel();
                                            return label != null
                                                    && (uri.endsWith("#" + label)
                                                            || uri.endsWith("/" + label));
                                        })
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(null);

                if (superClassUUID == null) {
                    continue;
                }

                inheritanceEdges.add(
                        EdgeDTO.builder()
                                .id(UUID.randomUUID().toString())
                                .type(INHERITANCE_EDGE_TYPE)
                                .source(mergedClass.getUuid())
                                .target(superClassUUID)
                                .data(null)
                                .build());
            }
        }
        return inheritanceEdges;
    }

    /**
     * Assembles association edges from associationPairs of each MergedClassDTO. Deduplicates by
     * sorted UUID pair.
     */
    private List<EdgeDTO> assembleAssociationEdgeDTOList(
            CrossProfileDiagramDTO diagram, Map<String, UUID> classUriToUUIDMap) {
        List<EdgeDTO> associationEdges = new ArrayList<>();
        var handledPairs = new HashSet<String>();

        for (var mergedClass : diagram.getClasses()) {
            if (CollectionUtils.isEmpty(mergedClass.getAssociationPairs())) {
                continue;
            }
            for (GraphSourceDTO<AssociationPairDTO> graphSourced :
                    mergedClass.getAssociationPairs()) {
                var pair = graphSourced.getValue();
                var from = pair.getFrom();
                var to = pair.getTo();

                if (from == null || to == null) {
                    continue;
                }

                var sourceUUID = mergedClass.getUuid();
                var targetUUID =
                        to.getRange() != null
                                ? classUriToUUIDMap.entrySet().stream()
                                        .filter(
                                                e -> {
                                                    String uri = e.getKey();
                                                    String rangeLabel = to.getRange().getLabel();
                                                    return rangeLabel != null
                                                            && (uri.endsWith("#" + rangeLabel)
                                                                    || uri.endsWith(
                                                                            "/" + rangeLabel));
                                                })
                                        .map(Map.Entry::getValue)
                                        .findFirst()
                                        .orElse(null)
                                : null;

                if (targetUUID == null) {
                    continue;
                }

                String pairKey =
                        sourceUUID.compareTo(targetUUID) < 0
                                ? sourceUUID + "|" + targetUUID
                                : targetUUID + "|" + sourceUUID;
                if (handledPairs.contains(pairKey)) {
                    continue;
                }
                handledPairs.add(pairKey);

                var edgeDataDTO =
                        EdgeDataDTO.builder()
                                .toMultiplicity(to.getMultiplicity())
                                .fromMultiplicity(from.getMultiplicity())
                                .useToAssociation(to.isAssociationUsed())
                                .useFromAssociation(from.isAssociationUsed())
                                .graphUri(graphSourced.getGraphUri())
                                .color(graphSourced.getGraphColor())
                                .build();

                associationEdges.add(
                        EdgeDTO.builder()
                                .id(UUID.randomUUID().toString())
                                .type(ASSOCIATION_EDGE_TYPE)
                                .source(sourceUUID)
                                .target(targetUUID)
                                .data(edgeDataDTO)
                                .build());
            }
        }
        return associationEdges;
    }
}
