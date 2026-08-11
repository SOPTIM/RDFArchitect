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
import lombok.extern.slf4j.Slf4j;

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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
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
     * Reads the sources off a private snapshot of each source graph, skipping the ones their graph
     * does not contain (any more). The returned facades resolve their properties lazily, so they
     * must not be bound to the graph of an open transaction: the caller keeps using them after this
     * method returns, and pasting within one schema would otherwise need a read and a write
     * transaction on the same graph context at once.
     */
    public List<ResolvedSource> readSources(List<CopyClassSource> sources) {
        return readSources(sources, newSourceSnapshots());
    }

    public SourceSnapshots newSourceSnapshots() {
        return new SourceSnapshots();
    }

    /**
     * Reads the sources off {@code snapshots}, taking a snapshot of every source graph they do not
     * hold yet. Callers that read from the same graphs more than once pass the same snapshots to
     * copy each of those graphs only once.
     */
    public List<ResolvedSource> readSources(
            List<CopyClassSource> sources, SourceSnapshots snapshots) {
        return sources.stream()
                .map(source -> toResolvedSource(snapshots.of(source.graphIdentifier()), source))
                .filter(Objects::nonNull)
                .toList();
    }

    private Model snapshotOf(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(GraphUtils.deepCopy(ctx.getRdfGraph()));
        }
    }

    private ResolvedSource toResolvedSource(Model model, CopyClassSource source) {
        var classUUID = source.classUUID().toString();
        if (!model.listSubjectsWithProperty(RDFA.uuid, classUUID).hasNext()) {
            log.warn(
                    "Skipping source class '{}' because graph '{}' does not contain it.",
                    classUUID,
                    source.graphIdentifier().graphUri());
            return null;
        }
        return new ResolvedSource(
                source.graphIdentifier(),
                new CIMClass(source.graphIdentifier().graphUri(), model, source.classUUID()));
    }

    public record ResolvedSource(GraphIdentifier graphIdentifier, ICIMClass cimClass) {}

    public final class SourceSnapshots {

        private final Map<GraphIdentifier, Model> modelsByGraph = new LinkedHashMap<>();

        private Model of(GraphIdentifier graphIdentifier) {
            return modelsByGraph.computeIfAbsent(
                    graphIdentifier, CopyClassSourceReader.this::snapshotOf);
        }
    }
}
