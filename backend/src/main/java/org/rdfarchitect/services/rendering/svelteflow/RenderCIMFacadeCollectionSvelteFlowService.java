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

package org.rdfarchitect.services.rendering.svelteflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.SuperClassDTO;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAssociation;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * Converts a {@link ICIMModelFacade} to a DTO Record that contains two JSON arrays with nodes and
 * edges used to render a UML diagram using the JavaScript library SvelteFlow.
 */
@Service
@ConditionalOnProperty(
        name = "rendering.renderer",
        havingValue = "svelteflow",
        matchIfMissing = true)
public class RenderCIMFacadeCollectionSvelteFlowService implements RenderCIMFacadeCollectionUseCase {

    // CONSTANTS FOR SVELTEFLOW CUSTOM NODE/EDGE TYPES
    private static final String CLASS_NODE_TYPE = "class";
    private static final String INHERITANCE_EDGE_TYPE = "inheritance";
    private static final String ASSOCIATION_EDGE_TYPE = "association";

    private static final String DEFAULT_PACKAGE = "default";

    @Override
    public RenderingDataDTO renderUML(
            ICIMModelFacade cimModel, GraphFilter filter, RenderingLayoutData layoutData) {
        var classes = selectClasses(cimModel, filter);
        if (classes.isEmpty()) {
            return createEmptyDiagram();
        }

        var renderContext =
                new RenderContext(
                        new ArrayList<>(classes.values()),
                        classes.values().stream()
                                .map(ICIMClass::getUuid)
                                .collect(Collectors.toSet()),
                        filter,
                        layoutData);

        var nodes = assembleNodeDTOList(renderContext);
        var edges = assembleEdgeDTOList(renderContext);

        return SvelteFlowDTO.builder().nodes(nodes).edges(edges).build();
    }

    private SvelteFlowDTO createEmptyDiagram() {
        return SvelteFlowDTO.builder().nodes(List.of()).edges(List.of()).build();
    }

    private Map<String, ICIMClass> selectClasses(ICIMModelFacade cimModel, GraphFilter filter) {
        var classes = new LinkedHashMap<String, ICIMClass>();

        if (!CollectionUtils.isEmpty(filter.getAllowedUUIDs())) {
            var allowedUUIDs =
                    filter.getAllowedUUIDs().stream()
                            .map(UUID::fromString)
                            .collect(Collectors.toSet());
            for (var cimClass : cimModel.getCIMClasses()) {
                if (allowedUUIDs.contains(cimClass.getUuid())) {
                    classes.put(cimClass.getUri().toString(), cimClass);
                }
            }
            return classes;
        }

        var category = cimModel.getCIMClassCategory(resolvePackageUUID(filter));
        if (category == null) {
            return classes;
        }
        for (var cimClass : category.getClasses()) {
            classes.put(cimClass.getUri().toString(), cimClass);
        }

        if (filter.isIncludeRelationsToExternalPackages()) {
            addExternallyRelatedClasses(cimModel, filter, classes);
        }

        return classes;
    }

    private UUID resolvePackageUUID(GraphFilter filter) {
        if (filter.getPackageUUID() == null || filter.getPackageUUID().equals(DEFAULT_PACKAGE)) {
            return null;
        }
        return UUID.fromString(filter.getPackageUUID());
    }

    private void addExternallyRelatedClasses(
            ICIMModelFacade cimModel, GraphFilter filter, Map<String, ICIMClass> classes) {
        var classesInPackage = List.copyOf(classes.values());

        if (filter.isIncludeAssociations()) {
            for (var cimClass : classesInPackage) {
                for (var association : cimClass.getAssociations()) {
                    addExternallyRelatedClass(classes, association.getRange());
                }
            }
        }

        if (filter.isIncludeInheritance()) {
            var packageUris = classes.keySet();
            for (var cimClass : classesInPackage) {
                for (var superClass : cimClass.getSuperClasses()) {
                    addExternallyRelatedClass(classes, superClass);
                }
            }
            for (var cimClass : cimModel.getCIMClasses()) {
                if (classes.containsKey(cimClass.getUri().toString())) {
                    continue;
                }
                var extendsPackageClass =
                        cimClass.getSuperClasses().stream()
                                .anyMatch(
                                        superClass ->
                                                packageUris.contains(
                                                        superClass.getUri().toString()));
                if (extendsPackageClass) {
                    classes.put(cimClass.getUri().toString(), cimClass);
                }
            }
        }
    }

    private void addExternallyRelatedClass(Map<String, ICIMClass> classes, ICIMClass cimClass) {
        if (cimClass.getUuid() == null || classes.containsKey(cimClass.getUri().toString())) {
            return;
        }
        classes.put(cimClass.getUri().toString(), cimClass);
    }

    private List<NodeDTO> assembleNodeDTOList(RenderContext renderContext) {
        List<NodeDTO> nodeDTOs = new ArrayList<>();
        for (var cimClass : renderContext.classes()) {
            nodeDTOs.add(assembleNodeDTO(renderContext, cimClass));
        }
        return nodeDTOs;
    }

    private NodeDTO assembleNodeDTO(RenderContext renderContext, ICIMClass cimClass) {
        var dop =
                renderContext.layoutingData() != null
                        ? renderContext
                                .layoutingData()
                                .getClassLayoutingData()
                                .get(cimClass.getUuid())
                        : null;

        var positionDTO =
                dop != null
                        ? PositionDTO.builder()
                                .x(dop.getPosition().getX())
                                .y(dop.getPosition().getY())
                                .z(dop.getPosition().getZ())
                                .build()
                        : PositionDTO.builder().x(0).y(0).z(0).build();

        var nodeDataDTO =
                NodeDataDTO.builder()
                        .graphUri(cimClass.getGraphUri())
                        .label(cimClass.getLabel().getValue())
                        .belongsToCategory(
                                cimClass.getBelongsToCategory() != null
                                        ? cimClass.getBelongsToCategory().getLabel().getValue()
                                        : null)
                        .stereotypes(getClassStereotypes(cimClass))
                        .attributes(getClassAttributes(renderContext, cimClass))
                        .enumEntries(getClassEnumEntries(renderContext, cimClass))
                        .superClasses(getClassSuperClasses(renderContext, cimClass))
                        .build();

        return NodeDTO.builder()
                .id(cimClass.getUuid())
                .type(CLASS_NODE_TYPE)
                .position(positionDTO)
                .data(nodeDataDTO)
                .build();
    }

    private List<AttributeDTO> getClassAttributes(
            RenderContext renderContext, ICIMClass cimClass) {
        if (!renderContext.filter().isIncludeAttributes()) {
            return List.of();
        }

        List<AttributeDTO> attributeDTOs = new ArrayList<>();
        for (var cimAttribute : cimClass.getAttributes()) {
            attributeDTOs.add(
                    AttributeDTO.builder()
                            .label(cimAttribute.getLabel().getValue())
                            .type(cimAttribute.getDataType().getLabel().getValue())
                            .multiplicity(extractMultiplicityString(cimAttribute.getMultiplicity()))
                            .build());
        }
        return attributeDTOs;
    }

    private List<String> getClassStereotypes(ICIMClass cimClass) {
        var stereotypes = cimClass.getStereotypes();
        var stereotypesToRender = new ArrayList<String>();

        if (CollectionUtils.isEmpty(stereotypes)
                || !stereotypes.contains(new CIMSStereotype(CIMStereotypes.concrete.toString()))) {
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

    private List<EnumEntryDTO> getClassEnumEntries(
            RenderContext renderContext, ICIMClass cimClass) {
        if (!renderContext.filter().isIncludeEnumEntries()) {
            return List.of();
        }

        List<EnumEntryDTO> enumEntries = new ArrayList<>();
        for (var cimEnumEntry : cimClass.getEnumEntries()) {
            enumEntries.add(
                    EnumEntryDTO.builder().label(cimEnumEntry.getLabel().getValue()).build());
        }
        return enumEntries;
    }

    private List<SuperClassDTO> getClassSuperClasses(
            RenderContext renderContext, ICIMClass cimClass) {
        var superClassDTOs = new ArrayList<SuperClassDTO>();
        var visitedUris = new HashSet<String>();
        visitedUris.add(cimClass.getUri().toString());

        var queue = new ArrayDeque<>(cimClass.getSuperClasses());
        while (!queue.isEmpty()) {
            var superClass = queue.poll();
            if (!visitedUris.add(superClass.getUri().toString())) {
                continue;
            }
            superClassDTOs.add(
                    SuperClassDTO.builder()
                            .uuid(superClass.getUuid())
                            .label(superClass.getLabel().getValue())
                            .attributes(getClassAttributes(renderContext, superClass))
                            .enumEntries(getClassEnumEntries(renderContext, superClass))
                            .build());
            queue.addAll(superClass.getSuperClasses());
        }

        return superClassDTOs;
    }

    private List<EdgeDTO> assembleEdgeDTOList(RenderContext renderContext) {
        List<EdgeDTO> edgeDTOList = new ArrayList<>();
        edgeDTOList.addAll(assembleInheritanceEdgeDTOList(renderContext));
        edgeDTOList.addAll(assembleAssociationEdgeDTOList(renderContext));
        return edgeDTOList;
    }

    private List<EdgeDTO> assembleInheritanceEdgeDTOList(RenderContext renderContext) {
        if (!renderContext.filter().isIncludeInheritance()) {
            return List.of();
        }

        List<EdgeDTO> inheritanceEdgeDTOList = new ArrayList<>();
        for (var cimClass : renderContext.classes()) {
            for (var superClass : cimClass.getSuperClasses()) {
                if (superClass.getUuid() == null
                        || !renderContext.nodeUUIDs().contains(superClass.getUuid())) {
                    continue;
                }
                inheritanceEdgeDTOList.add(
                        EdgeDTO.builder()
                                .id(UUID.randomUUID().toString())
                                .type(INHERITANCE_EDGE_TYPE)
                                .source(cimClass.getUuid())
                                .target(superClass.getUuid())
                                .data(null)
                                .build());
            }
        }
        return inheritanceEdgeDTOList;
    }

    private List<EdgeDTO> assembleAssociationEdgeDTOList(RenderContext renderContext) {
        if (!renderContext.filter().isIncludeAssociations()) {
            return List.of();
        }

        List<EdgeDTO> associationEdgeDTOList = new ArrayList<>();
        var handledAssociationUris = new HashSet<String>();
        for (var cimClass : renderContext.classes()) {
            for (var from : cimClass.getAssociations()) {
                if (handledAssociationUris.contains(from.getUri().toString())) {
                    continue;
                }
                var rangeUUID = from.getRange().getUuid();
                if (rangeUUID == null || !renderContext.nodeUUIDs().contains(rangeUUID)) {
                    continue;
                }
                var to = from.getInverseAssociation();

                associationEdgeDTOList.add(assembleAssociationEdgeDTO(cimClass, from, to));

                handledAssociationUris.add(from.getUri().toString());
                handledAssociationUris.add(to.getUri().toString());
            }
        }
        return associationEdgeDTOList;
    }

    private EdgeDTO assembleAssociationEdgeDTO(
            ICIMClass sourceClass, ICIMAssociation from, ICIMAssociation to) {
        var edgeDataDTO =
                EdgeDataDTO.builder()
                        .fromMultiplicity(extractMultiplicityString(from.getMultiplicity()))
                        .toMultiplicity(extractMultiplicityString(to.getMultiplicity()))
                        .useToAssociation(getAssociationUsedValue(from.getAssociationUsed()))
                        .useFromAssociation(getAssociationUsedValue(to.getAssociationUsed()))
                        .build();

        return EdgeDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(ASSOCIATION_EDGE_TYPE)
                .source(sourceClass.getUuid())
                .target(from.getRange().getUuid())
                .data(edgeDataDTO)
                .build();
    }

    private String extractMultiplicityString(CIMSMultiplicity multiplicity) {
        return multiplicity.getUri().getSuffix().replace("M:", "");
    }

    private boolean getAssociationUsedValue(CIMSAssociationUsed associationUsed) {
        var associationUsedValue = associationUsed.toString();
        return switch (associationUsedValue) {
            case "Yes" -> true;
            case "No" -> false;
            default ->
                    throw new IllegalArgumentException(
                            "Unexpected associationUsed value: " + associationUsedValue);
        };
    }

    private record RenderContext(
            List<ICIMClass> classes,
            Set<UUID> nodeUUIDs,
            GraphFilter filter,
            RenderingLayoutData layoutingData) {}
}
