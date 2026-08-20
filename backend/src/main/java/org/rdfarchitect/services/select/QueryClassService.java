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
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.models.cim.relations.ClassRelationsDTO;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryClassService
        implements GetClassInformationUseCase,
                ListSuperClassesUseCase,
                GetClassesReferencingThisClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper umlAdaptedClassMapper;
    private final ClassMapper mapper;
    private final SuperClassResolver superClassResolver;

    @Override
    public ClassUMLAdaptedDTO getClassInformation(
            GraphIdentifier graphIdentifier, String classUUID, boolean includeSuperClasses) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
            var cimClass =
                    CIMUMLObjectFactory.createCIMClassUMLAdapted(
                            graph, graphIdentifier.graphUri(), prefixMapping, classUUID);
            if (cimClass == null) {
                return referencedOnlyClassDTO(graph, classUUID);
            }
            var classDTO = umlAdaptedClassMapper.toDTO(cimClass);
            if (includeSuperClasses) {
                var expandedSuperClass =
                        superClassResolver.resolveSuperClass(
                                graph, graphIdentifier.graphUri(), prefixMapping, classUUID);
                if (expandedSuperClass != null) {
                    classDTO.setSuperClass(expandedSuperClass);
                }
            }
            return classDTO;
        }
    }

    /**
     * Builds the information for a class that is only referenced by other resources instead of
     * being defined in this graph. It carries nothing but its uri and uuid, so only the name can be
     * shown until the class is created.
     */
    private ClassUMLAdaptedDTO referencedOnlyClassDTO(Graph graph, String classUUID) {
        var model = ModelFactory.createModelForGraph(graph);
        var subjects = model.listSubjectsWithProperty(RDFA.uuid, classUUID).toList();
        if (subjects.size() != 1) {
            return null;
        }
        var resource = subjects.getFirst();
        if (!resource.isURIResource() || !CIMResourceUtils.isExternalResource(resource)) {
            return null;
        }
        var uri = new URI(resource.getURI());
        return ClassUMLAdaptedDTO.builder()
                .uuid(UUID.fromString(classUUID))
                .external(true)
                .prefix(uri.getPrefix())
                .label(uri.getSuffix())
                .stereotypes(List.of())
                .build();
    }

    @Override
    public List<ClassDTO> listSuperClasses(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            var superClassList = new ArrayList<CIMClass>();
            var cimObjectFetcher =
                    new CIMObjectFetcher(
                            graph,
                            graphIdentifier.graphUri(),
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var baseClass = cimObjectFetcher.fetchCIMClass(classUUID.toString());
            superClassList.add(
                    cimObjectFetcher.fetchCIMClass(baseClass.getSuperClass().getUri().toString()));

            for (var i = 0; i < superClassList.size(); i++) {
                var superClass = superClassList.get(i);
                if (superClass != null) {
                    superClassList.add(
                            cimObjectFetcher.fetchCIMClass(superClass.getUri().toString()));
                }
            }
            return mapper.toDTOList(superClassList);
        }
    }

    @Override
    public ClassRelationsDTO getClassesReferencingThisClass(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return CIMClassRelationFinder.getAllClassRelations(ctx.getRdfGraph(), classUUID);
        }
    }
}
