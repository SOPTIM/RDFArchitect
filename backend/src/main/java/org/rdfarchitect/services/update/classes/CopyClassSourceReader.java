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

package org.rdfarchitect.services.update.classes;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.PasteSourceClassDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CopyClassSourceReader {

    private final DatabasePort databasePort;
    private final ExpandURIUseCase expandURIUseCase;

    public List<CopyClassSource> toSources(List<PasteSourceClassDTO> sourceDTOs) {
        if (sourceDTOs == null) {
            return List.of();
        }
        return sourceDTOs.stream()
                .map(
                        dto -> {
                            var expandedGraphURI =
                                    expandURIUseCase.expandUri(
                                            dto.getSourceDatasetName(), dto.getSourceGraphURI());
                            var sourceGraphIdentifier =
                                    new GraphIdentifier(
                                            dto.getSourceDatasetName(), expandedGraphURI);
                            return new CopyClassSource(
                                    sourceGraphIdentifier, UUID.fromString(dto.getClassUUID()));
                        })
                .toList();
    }

    public List<CIMClassUMLAdapted> readSourceClasses(List<CopyClassSource> sources) {
        var sourcesByGraph =
                sources.stream()
                        .collect(
                                Collectors.groupingBy(
                                        CopyClassSource::graphIdentifier,
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        var classesBySource = new HashMap<CopyClassSource, CIMClassUMLAdapted>();
        sourcesByGraph.forEach(
                (graphIdentifier, graphSources) ->
                        classesBySource.putAll(readSourceClasses(graphIdentifier, graphSources)));
        return sources.stream().map(classesBySource::get).toList();
    }

    private Map<CopyClassSource, CIMClassUMLAdapted> readSourceClasses(
            GraphIdentifier graphIdentifier, List<CopyClassSource> sources) {

        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var classesBySource = new HashMap<CopyClassSource, CIMClassUMLAdapted>();
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            for (var source : sources) {
                classesBySource.put(
                        source,
                        CIMUMLObjectFactory.createCIMClassUMLAdapted(
                                ctx.getRdfGraph(),
                                graphIdentifier.graphUri(),
                                prefixMapping,
                                source.classUUID().toString()));
            }
        }
        return classesBySource;
    }
}
