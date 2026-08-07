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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.PasteSourceClassDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

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

    /**
     * Reads the sources off a private snapshot of each source graph. The returned facades resolve
     * their properties lazily, so they must not be bound to the graph of an open transaction: the
     * caller keeps using them after this method returns, and pasting within one schema would
     * otherwise need a read and a write transaction on the same graph context at once.
     */
    public List<ICIMClass> readSourceClasses(List<CopyClassSource> sources) {
        var modelsByGraph = new LinkedHashMap<GraphIdentifier, Model>();
        for (var source : sources) {
            modelsByGraph.computeIfAbsent(source.graphIdentifier(), this::snapshotOf);
        }
        return sources.stream()
                .map(source -> toClass(modelsByGraph.get(source.graphIdentifier()), source))
                .toList();
    }

    private Model snapshotOf(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(GraphUtils.deepCopy(ctx.getRdfGraph()));
        }
    }

    private ICIMClass toClass(Model model, CopyClassSource source) {
        var classUUID = source.classUUID().toString();
        if (!model.listSubjectsWithProperty(RDFA.uuid, classUUID).hasNext()) {
            return null;
        }
        return new CIMClass(source.graphIdentifier().graphUri(), model, source.classUUID());
    }
}
