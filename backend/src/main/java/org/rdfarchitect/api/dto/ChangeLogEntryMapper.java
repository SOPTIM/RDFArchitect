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

package org.rdfarchitect.api.dto;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.GraphFactory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.changelog.WorkspaceHistoryStep;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface ChangeLogEntryMapper {

    @Mapping(target = "contextDeltas", source = "deltas")
    @Mapping(target = "removedOnUndo", expression = "java(entry.removedOnUndo())")
    @Mapping(
            target = "affectedGraphUris",
            expression = "java(java.util.List.copyOf(entry.affectedGraphUris()))")
    @Mapping(
            target = "restorableGraphUris",
            expression = "java(java.util.List.copyOf(entry.restorableGraphUris()))")
    @Mapping(target = "affectedKinds", expression = "java(entry.affectedKinds())")
    @Mapping(
            target = "restoreTargetGraphUris",
            expression = "java(java.util.List.copyOf(entry.restoreTargetGraphUris()))")
    ChangeLogEntryDTO toDTO(WorkspaceChangeLogEntry entry);

    List<ChangeLogEntryDTO> toDTOList(List<WorkspaceChangeLogEntry> entries);

    HistoryStepDTO toDTO(WorkspaceHistoryStep step);

    /**
     * Maps the deltas of an entry to one context per participant. A participant can gain several
     * versions in one entry, e.g. the layout changed by renaming a class and again by the sync of
     * the layout within the same change; their deltas are shown as one context with what the entry
     * changed in the end.
     */
    default List<ContextDeltaDTO> toContextDeltaDTOs(List<ContextDelta> deltas) {
        var deltasByParticipant = new ArrayList<List<ContextDelta>>();
        for (var delta : deltas) {
            deltasByParticipant.stream()
                    .filter(
                            sameParticipant ->
                                    sameParticipant
                                                    .getFirst()
                                                    .participant()
                                                    .equals(delta.participant())
                                            && sameParticipant.getFirst().owner() == delta.owner())
                    .findFirst()
                    .ifPresentOrElse(
                            sameParticipant -> sameParticipant.add(delta),
                            () -> deltasByParticipant.add(new ArrayList<>(List.of(delta))));
        }
        return deltasByParticipant.stream()
                .map(
                        sameParticipant ->
                                sameParticipant.size() == 1
                                        ? toContextDeltaDTO(sameParticipant.getFirst())
                                        : toMergedContextDeltaDTO(sameParticipant))
                .toList();
    }

    private ContextDeltaDTO toMergedContextDeltaDTO(List<ContextDelta> deltas) {
        var dto = new ContextDeltaDTO();
        dto.setContextName(deltas.getFirst().contextName());
        dto.setGraphUri(deltas.getFirst().graphUri());
        var netTriples = netTriples(deltas);
        if (netTriples != null) {
            dto.setAdditions(mapTriples(netTriples.additions()));
            dto.setDeletions(mapTriples(netTriples.deletions()));
        }
        dto.setValues(netValues(deltas).stream().map(this::mapValueChange).toList());
        return dto;
    }

    /**
     * The triples the deltas added and removed in the end: a triple added by one and removed by a
     * later one was never there, nor was one removed and added again ever gone. {@code null} if the
     * deltas hold no triples or one of them is no longer known.
     */
    private static NetTriples netTriples(List<ContextDelta> deltas) {
        if (deltas.getFirst().additions() == null) {
            return null;
        }
        var additions = GraphFactory.createDefaultGraph();
        var deletions = GraphFactory.createDefaultGraph();
        for (var delta : deltas) {
            var added = delta.additions().get();
            var deleted = delta.deletions().get();
            if (added == null || deleted == null) {
                return null;
            }
            deleted.find()
                    .forEachRemaining(
                            triple -> {
                                if (additions.contains(triple)) {
                                    additions.delete(triple);
                                } else {
                                    deletions.add(triple);
                                }
                            });
            added.find()
                    .forEachRemaining(
                            triple -> {
                                if (deletions.contains(triple)) {
                                    deletions.delete(triple);
                                } else {
                                    additions.add(triple);
                                }
                            });
        }
        return new NetTriples(additions, deletions);
    }

    /** Every value as the first delta found it and the last one left it, unless it is unchanged. */
    private static List<ValueChange> netValues(List<ContextDelta> deltas) {
        var netValues = new LinkedHashMap<String, ValueChange>();
        for (var delta : deltas) {
            for (var value : delta.values()) {
                var before =
                        netValues.containsKey(value.key())
                                ? netValues.get(value.key()).before()
                                : value.before();
                netValues.put(value.key(), new ValueChange(value.key(), before, value.after()));
            }
        }
        return netValues.values().stream()
                .filter(value -> !Objects.equals(value.before(), value.after()))
                .toList();
    }

    /** The triples a merged delta added and removed. */
    record NetTriples(Graph additions, Graph deletions) {}

    default ContextDeltaDTO toContextDeltaDTO(ContextDelta contextDelta) {
        var dto = new ContextDeltaDTO();
        dto.setContextName(contextDelta.contextName());
        dto.setGraphUri(contextDelta.graphUri());
        dto.setAdditions(mapTriples(contextDelta.additions()));
        dto.setDeletions(mapTriples(contextDelta.deletions()));
        dto.setValues(contextDelta.values().stream().map(this::mapValueChange).toList());
        return dto;
    }

    default ValueChangeDTO mapValueChange(ValueChange valueChange) {
        return new ValueChangeDTO(valueChange.key(), valueChange.before(), valueChange.after());
    }

    default List<TripleDTO> mapTriples(WeakReference<Graph> graphReference) {
        if (graphReference == null) {
            return null;
        }
        return mapTriples(graphReference.get());
    }

    default List<TripleDTO> mapTriples(Graph graph) {
        if (graph == null) {
            return null;
        }
        return graph.stream()
                .filter(triple -> !triple.getPredicate().equals(RDFA.uuid.asNode()))
                .map(this::mapTriple)
                .toList();
    }

    default TripleDTO mapTriple(Triple triple) {
        if (triple == null) {
            return null;
        }
        return new TripleDTO(
                triple.getSubject().toString(),
                triple.getPredicate().toString(),
                triple.getObject().toString());
    }
}
