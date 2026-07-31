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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts a {@link ICIMModelFacade} to a DTO Record that contains two JSON arrays with nodes and
 * edges used to render a UML diagram using the JavaScript library SvelteFlow.
 */
@Service
@ConditionalOnProperty(
        name = "rendering.renderer",
        havingValue = "svelteflow",
        matchIfMissing = true)
public class RenderCIMFacadeCollectionSvelteFlowService
        implements RenderCIMFacadeCollectionUseCase {

    // CONSTANTS FOR SVELTEFLOW CUSTOM NODE/EDGE TYPES
    private static final String CLASS_NODE_TYPE = "class";
    private static final String INHERITANCE_EDGE_TYPE = "inheritance";
    private static final String ASSOCIATION_EDGE_TYPE = "association";

    private static final String DEFAULT_PACKAGE = "default";

    @Override
    public RenderingDataDTO renderUML(
            ICIMModelFacade cimModel,
            GraphFilter filter,
            RenderingLayoutData layoutData,
            List<CIMProfileModel> otherProfiles,
            String primaryColor) {
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
                        layoutData,
                        cimModel.getGraphUri(),
                        primaryColor,
                        buildProfileLookups(filter, otherProfiles));

        var nodes = assembleNodeDTOList(renderContext);
        var edges = assembleEdgeDTOList(renderContext);

        return SvelteFlowDTO.builder().nodes(nodes).edges(edges).build();
    }

    private List<ProfileLookup> buildProfileLookups(
            GraphFilter filter, List<CIMProfileModel> otherProfiles) {
        if (!filter.isIncludePropertiesFromOtherProfiles() || otherProfiles == null) {
            return List.of();
        }
        var lookups = new ArrayList<ProfileLookup>();
        for (var profile : otherProfiles) {
            var classByUri = new LinkedHashMap<String, ICIMClass>();
            for (var cimClass : profile.model().getCIMClasses()) {
                classByUri.putIfAbsent(cimClass.getUri().toString(), cimClass);
            }
            lookups.add(new ProfileLookup(profile.graphUri(), profile.color(), classByUri));
        }
        return lookups;
    }

    @Override
    public RenderingDataDTO renderMergedUML(
            List<CIMProfileModel> profiles, RenderingLayoutData layoutData) {
        var mergedClasses = mergeClassesByUri(profiles);
        if (mergedClasses.isEmpty()) {
            return createEmptyDiagram();
        }

        var nodes = new ArrayList<NodeDTO>();
        for (var merged : mergedClasses.values()) {
            nodes.add(assembleMergedNode(merged, mergedClasses, layoutData));
        }
        var edges = assembleMergedEdges(mergedClasses);

        return SvelteFlowDTO.builder().nodes(nodes).edges(edges).build();
    }

    private Map<String, MergedFacadeClass> mergeClassesByUri(List<CIMProfileModel> profiles) {
        var merged = new LinkedHashMap<String, MergedFacadeClass>();
        if (profiles == null) {
            return merged;
        }
        for (var profile : profiles) {
            for (var cimClass : profile.model().getCIMClasses()) {
                var key = cimClass.getUri().toString();
                var entry =
                        merged.computeIfAbsent(
                                key,
                                classUri ->
                                        new MergedFacadeClass(
                                                classUri,
                                                cimClass.getLabel().getValue(),
                                                UUID.nameUUIDFromBytes(
                                                        classUri.getBytes(StandardCharsets.UTF_8)),
                                                new ArrayList<>()));
                entry.sources()
                        .add(new MergedSource(profile.graphUri(), profile.color(), cimClass));
            }
        }
        return merged;
    }

    private NodeDTO assembleMergedNode(
            MergedFacadeClass merged,
            Map<String, MergedFacadeClass> allClasses,
            RenderingLayoutData layoutData) {
        var dop =
                layoutData != null && layoutData.getClassLayoutingData() != null
                        ? layoutData.getClassLayoutingData().get(merged.uuid())
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
                        .graphUri(merged.sources().getFirst().graphUri())
                        .label(merged.label())
                        .belongsToCategory(null)
                        .stereotypes(mergedStereotypes(merged))
                        .attributes(mergedAttributes(merged))
                        .enumEntries(mergedEnumEntries(merged))
                        .superClasses(mergedSuperClasses(merged, allClasses))
                        .build();

        return NodeDTO.builder()
                .id(merged.uuid())
                .type(CLASS_NODE_TYPE)
                .position(positionDTO)
                .data(nodeDataDTO)
                .build();
    }

    private List<String> mergedStereotypes(MergedFacadeClass merged) {
        var union = new ArrayList<CIMSStereotype>();
        for (var source : merged.sources()) {
            for (var stereotype : source.cimClass().getStereotypes()) {
                if (!union.contains(stereotype)) {
                    union.add(stereotype);
                }
            }
        }
        return stereotypesToRender(union);
    }

    private List<AttributeDTO> mergedAttributes(MergedFacadeClass merged) {
        var attributes = new ArrayList<AttributeDTO>();
        for (var source : merged.sources()) {
            for (var attribute : source.cimClass().getAttributes()) {
                attributes.add(toAttributeDTO(attribute, source.graphUri(), source.color()));
            }
        }
        return attributes;
    }

    private List<EnumEntryDTO> mergedEnumEntries(MergedFacadeClass merged) {
        var enumEntries = new ArrayList<EnumEntryDTO>();
        for (var source : merged.sources()) {
            for (var enumEntry : source.cimClass().getEnumEntries()) {
                enumEntries.add(
                        EnumEntryDTO.builder()
                                .label(enumEntry.getLabel().getValue())
                                .graphUri(source.graphUri())
                                .color(source.color())
                                .build());
            }
        }
        return enumEntries;
    }

    private List<SuperClassDTO> mergedSuperClasses(
            MergedFacadeClass merged, Map<String, MergedFacadeClass> allClasses) {
        var superClassDTOs = new ArrayList<SuperClassDTO>();
        var visited = new HashSet<String>();
        visited.add(merged.uri());

        var queue = new ArrayDeque<>(collectSuperClassRefs(merged));
        while (!queue.isEmpty()) {
            var ref = queue.poll();
            if (!visited.add(ref.uri())) {
                continue;
            }
            var resolved = allClasses.get(ref.uri());
            if (resolved != null) {
                superClassDTOs.add(
                        SuperClassDTO.builder()
                                .uuid(resolved.uuid())
                                .label(resolved.label())
                                .attributes(mergedAttributes(resolved))
                                .enumEntries(mergedEnumEntries(resolved))
                                .build());
                queue.addAll(collectSuperClassRefs(resolved));
            } else {
                superClassDTOs.add(
                        SuperClassDTO.builder()
                                .label(ref.label())
                                .attributes(List.of())
                                .enumEntries(List.of())
                                .build());
            }
        }
        return superClassDTOs;
    }

    private List<SuperClassRef> collectSuperClassRefs(MergedFacadeClass merged) {
        var refs = new LinkedHashMap<String, SuperClassRef>();
        for (var source : merged.sources()) {
            for (var superClass : source.cimClass().getSuperClasses()) {
                refs.putIfAbsent(
                        superClass.getUri().toString(),
                        new SuperClassRef(
                                superClass.getUri().toString(), superClass.getLabel().getValue()));
            }
        }
        return new ArrayList<>(refs.values());
    }

    private List<EdgeDTO> assembleMergedEdges(Map<String, MergedFacadeClass> mergedClasses) {
        var edges = new ArrayList<EdgeDTO>();
        edges.addAll(assembleMergedInheritanceEdges(mergedClasses));
        edges.addAll(assembleMergedAssociationEdges(mergedClasses));
        return edges;
    }

    private List<EdgeDTO> assembleMergedInheritanceEdges(
            Map<String, MergedFacadeClass> mergedClasses) {
        var edges = new ArrayList<EdgeDTO>();
        for (var merged : mergedClasses.values()) {
            for (var ref : collectSuperClassRefs(merged)) {
                var superClass = mergedClasses.get(ref.uri());
                if (superClass == null) {
                    continue;
                }
                edges.add(
                        EdgeDTO.builder()
                                .id(UUID.randomUUID().toString())
                                .type(INHERITANCE_EDGE_TYPE)
                                .source(merged.uuid())
                                .target(superClass.uuid())
                                .data(null)
                                .build());
            }
        }
        return edges;
    }

    private List<EdgeDTO> assembleMergedAssociationEdges(
            Map<String, MergedFacadeClass> mergedClasses) {
        var edges = new ArrayList<EdgeDTO>();
        var handledPairs = new HashSet<String>();
        for (var merged : mergedClasses.values()) {
            for (var source : merged.sources()) {
                for (var association : source.cimClass().getAssociations()) {
                    if (!association.isRenderable()) {
                        continue;
                    }
                    var range = association.getRange();
                    var target = mergedClasses.get(range.getUri().toString());
                    if (target == null) {
                        continue;
                    }
                    var pairKey =
                            merged.uuid().compareTo(target.uuid()) < 0
                                    ? merged.uuid() + "|" + target.uuid()
                                    : target.uuid() + "|" + merged.uuid();
                    if (!handledPairs.add(pairKey)) {
                        continue;
                    }
                    var inverse = association.getInverseAssociation();
                    var edgeData =
                            EdgeDataDTO.builder()
                                    .fromMultiplicity(
                                            extractMultiplicityString(
                                                    association.getMultiplicity()))
                                    .toMultiplicity(
                                            extractMultiplicityString(inverse.getMultiplicity()))
                                    .useToAssociation(
                                            getAssociationUsedValue(
                                                    association.getAssociationUsed()))
                                    .useFromAssociation(
                                            getAssociationUsedValue(inverse.getAssociationUsed()))
                                    .graphUri(source.graphUri())
                                    .color(source.color())
                                    .build();
                    edges.add(
                            EdgeDTO.builder()
                                    .id(UUID.randomUUID().toString())
                                    .type(ASSOCIATION_EDGE_TYPE)
                                    .source(merged.uuid())
                                    .target(target.uuid())
                                    .data(edgeData)
                                    .build());
                }
            }
        }
        return edges;
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
                    if (association.isRenderable()) {
                        addExternallyRelatedClass(classes, association.getRange());
                    }
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

    private List<AttributeDTO> getClassAttributes(RenderContext renderContext, ICIMClass cimClass) {
        if (!renderContext.filter().isIncludeAttributes()) {
            return List.of();
        }

        boolean merge = renderContext.filter().isIncludePropertiesFromOtherProfiles();
        String ownGraphUri = merge ? renderContext.primaryGraphUri() : null;
        String ownColor = merge ? renderContext.primaryColor() : null;

        List<AttributeDTO> attributeDTOs = new ArrayList<>();
        for (var cimAttribute : cimClass.getAttributes()) {
            attributeDTOs.add(toAttributeDTO(cimAttribute, ownGraphUri, ownColor));
        }

        if (merge) {
            for (var profile : renderContext.otherProfiles()) {
                var match = profile.classByUri().get(cimClass.getUri().toString());
                if (match == null) {
                    continue;
                }
                for (var cimAttribute : match.getAttributes()) {
                    attributeDTOs.add(
                            toAttributeDTO(cimAttribute, profile.graphUri(), profile.color()));
                }
            }
        }
        return attributeDTOs;
    }

    private AttributeDTO toAttributeDTO(ICIMAttribute cimAttribute, String graphUri, String color) {
        return AttributeDTO.builder()
                .label(cimAttribute.getLabel().getValue())
                .type(cimAttribute.getDataType().getLabel().getValue())
                .multiplicity(extractMultiplicityString(cimAttribute.getMultiplicity()))
                .graphUri(graphUri)
                .color(color)
                .build();
    }

    private List<String> getClassStereotypes(ICIMClass cimClass) {
        return stereotypesToRender(cimClass.getStereotypes());
    }

    private List<String> stereotypesToRender(List<CIMSStereotype> stereotypes) {
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

        boolean merge = renderContext.filter().isIncludePropertiesFromOtherProfiles();
        String ownGraphUri = merge ? renderContext.primaryGraphUri() : null;
        String ownColor = merge ? renderContext.primaryColor() : null;

        List<EnumEntryDTO> enumEntries = new ArrayList<>();
        for (var cimEnumEntry : cimClass.getEnumEntries()) {
            enumEntries.add(
                    EnumEntryDTO.builder()
                            .label(cimEnumEntry.getLabel().getValue())
                            .graphUri(ownGraphUri)
                            .color(ownColor)
                            .build());
        }

        if (merge) {
            for (var profile : renderContext.otherProfiles()) {
                var match = profile.classByUri().get(cimClass.getUri().toString());
                if (match == null) {
                    continue;
                }
                for (var cimEnumEntry : match.getEnumEntries()) {
                    enumEntries.add(
                            EnumEntryDTO.builder()
                                    .label(cimEnumEntry.getLabel().getValue())
                                    .graphUri(profile.graphUri())
                                    .color(profile.color())
                                    .build());
                }
            }
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
                if (handledAssociationUris.contains(from.getUri().toString())
                        || !from.isRenderable()) {
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
            RenderingLayoutData layoutingData,
            String primaryGraphUri,
            String primaryColor,
            List<ProfileLookup> otherProfiles) {}

    private record ProfileLookup(
            String graphUri, String color, Map<String, ICIMClass> classByUri) {}

    private record MergedFacadeClass(
            String uri, String label, UUID uuid, List<MergedSource> sources) {}

    private record MergedSource(String graphUri, String color, ICIMClass cimClass) {}

    private record SuperClassRef(String uri, String label) {}
}
