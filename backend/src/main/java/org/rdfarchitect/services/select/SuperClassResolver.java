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
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.models.cim.data.dto.facade.CIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SuperClassResolver {

    private static final Logger logger = LoggerFactory.getLogger(SuperClassResolver.class);

    private final ClassUMLAdaptedMapper umlAdaptedClassMapper;

    public List<ClassUMLAdaptedDTO> resolveSuperClasses(
            Graph graph, String graphUri, PrefixMapping prefixMapping, String rootClassUUID) {
        var rootUuid = UUID.fromString(rootClassUUID);
        var model = ModelFactory.createModelForGraph(graph);
        var rootClass = new CIMClass(graphUri, model, rootUuid);

        var visited = new HashSet<UUID>();
        visited.add(rootUuid);
        return buildNodes(graph, graphUri, prefixMapping, safeSuperClasses(rootClass), visited);
    }

    private List<ClassUMLAdaptedDTO> buildNodes(
            Graph graph,
            String graphUri,
            PrefixMapping prefixMapping,
            List<ICIMClass> superClasses,
            Set<UUID> visited) {
        var nodes = new ArrayList<ClassUMLAdaptedDTO>();
        for (var superClass : superClasses) {
            var uuid = superClass.getUuid();
            if (uuid == null || !visited.add(uuid)) {
                continue;
            }
            var superClassCim =
                    CIMUMLObjectFactory.createCIMClassUMLAdapted(
                            graph, graphUri, prefixMapping, uuid.toString());
            if (superClassCim == null) {
                continue;
            }
            var dto = umlAdaptedClassMapper.toDTO(superClassCim);
            dto.setSuperClasses(
                    buildNodes(
                            graph, graphUri, prefixMapping, safeSuperClasses(superClass), visited));
            nodes.add(dto);
        }
        return nodes;
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
