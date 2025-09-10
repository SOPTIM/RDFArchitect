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
import org.apache.jena.query.TxnType;
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.models.cim.search.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.search.data.dto.CIMClass;
import org.rdfarchitect.models.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.models.cim.relations.ClassRelationsDTO;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryClassService implements GetClassInformationUseCase, ListSuperClassesUseCase, GetClassesReferencingThisClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper umlAdaptedClassMapper;
    private final ClassMapper mapper;

    @Override
    public ClassUMLAdaptedDTO getClassInformation(GraphIdentifier graphIdentifier, String classUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);

            var cimClass =
                      CIMUMLObjectFactory.createCIMClassUMLAdapted(graph, graphIdentifier.getGraphUri(), databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                                                                   classUUID);
            return umlAdaptedClassMapper.toDTO(cimClass);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    @Override
    public List<ClassDTO> listSuperClasses(GraphIdentifier graphIdentifier, UUID classUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);

            var superClassList = new ArrayList<CIMClass>();
            var cimObjectFetcher = new CIMObjectFetcher(graph, graphIdentifier.getGraphUri(), databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));
            var baseClass = cimObjectFetcher.fetchCIMClass(classUUID.toString());
            superClassList.add(cimObjectFetcher.fetchCIMClass(baseClass.getSuperClass().getUri().toString()));

            for (var i = 0; i < superClassList.size(); i++) {
                var superClass = superClassList.get(i);
                if (superClass != null) {
                    superClassList.add(cimObjectFetcher.fetchCIMClass(superClass.getUri().toString()));
                }
            }
            return mapper.toDTOList(superClassList);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    @Override
    public ClassRelationsDTO getClassesReferencingThisClass(GraphIdentifier graphIdentifier, UUID classUUID) {
        GraphRewindable graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            return CIMClassRelationFinder.getAllClassRelations(graph, classUUID);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }
}
