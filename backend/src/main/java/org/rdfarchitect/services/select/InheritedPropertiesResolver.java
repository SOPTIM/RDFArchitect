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

package org.rdfarchitect.services.select;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.association.InheritedAssociationGroupDTO;
import org.rdfarchitect.api.dto.attributes.InheritedAttributeGroupDTO;
import org.rdfarchitect.models.cim.data.dto.facade.CIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InheritedPropertiesResolver {

    private static final Logger logger = LoggerFactory.getLogger(InheritedPropertiesResolver.class);

    private final ClassUMLAdaptedMapper umlAdaptedClassMapper;

    public record InheritedProperties(
            List<InheritedAttributeGroupDTO> attributes,
            List<InheritedAssociationGroupDTO> associations) {}

    public InheritedProperties resolveInheritedProperties(
            Graph graph, String graphUri, PrefixMapping prefixMapping, String rootClassUUID) {
        var rootUuid = UUID.fromString(rootClassUUID);
        var model = ModelFactory.createModelForGraph(graph);
        var rootClass = new CIMClass(graphUri, model, rootUuid);

        var attributeGroups = new ArrayList<InheritedAttributeGroupDTO>();
        var associationGroups = new ArrayList<InheritedAssociationGroupDTO>();
        var visited = new HashSet<UUID>();
        visited.add(rootUuid);

        var queue = new ArrayDeque<>(safeSuperClasses(rootClass));
        while (!queue.isEmpty()) {
            var superClass = queue.poll();
            var uuid = superClass.getUuid();
            if (uuid == null || !visited.add(uuid)) {
                continue;
            }
            addGroups(graph, graphUri, prefixMapping, uuid, attributeGroups, associationGroups);
            queue.addAll(safeSuperClasses(superClass));
        }
        return new InheritedProperties(attributeGroups, associationGroups);
    }

    private void addGroups(
            Graph graph,
            String graphUri,
            PrefixMapping prefixMapping,
            UUID superClassUuid,
            List<InheritedAttributeGroupDTO> attributeGroups,
            List<InheritedAssociationGroupDTO> associationGroups) {
        var superClassCim =
                CIMUMLObjectFactory.createCIMClassUMLAdapted(
                        graph, graphUri, prefixMapping, superClassUuid.toString());
        if (superClassCim == null) {
            return;
        }
        var dto = umlAdaptedClassMapper.toDTO(superClassCim);
        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
            attributeGroups.add(
                    InheritedAttributeGroupDTO.builder()
                            .sourceClassUuid(superClassUuid)
                            .sourceClassPrefix(dto.getPrefix())
                            .sourceClassLabel(dto.getLabel())
                            .attributes(dto.getAttributes())
                            .build());
        }
        if (dto.getAssociationPairs() != null && !dto.getAssociationPairs().isEmpty()) {
            associationGroups.add(
                    InheritedAssociationGroupDTO.builder()
                            .sourceClassUuid(superClassUuid)
                            .sourceClassPrefix(dto.getPrefix())
                            .sourceClassLabel(dto.getLabel())
                            .associations(dto.getAssociationPairs())
                            .build());
        }
    }

    private List<ICIMClass> safeSuperClasses(ICIMClass cimClass) {
        try {
            return cimClass.getSuperClasses();
        } catch (RuntimeException e) {
            logger.warn(
                    "Failed to read superclasses for class {}: {}",
                    cimClass.getUri(),
                    e.getMessage());
            return List.of();
        }
    }
}
