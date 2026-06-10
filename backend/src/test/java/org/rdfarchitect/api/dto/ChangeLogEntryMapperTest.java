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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class ChangeLogEntryMapperTest {

    private final ChangeLogEntryMapper changeLogEntryMapper =
            Mappers.getMapper(ChangeLogEntryMapper.class);

    private static final LocalDateTime TIMESTAMP = LocalDateTime.now();
    private static final UUID CHANGE_ID = UUID.randomUUID();
    private static final String MESSAGE = "test message";
    private static final String SUB = "sub";
    private static final String PRED = "pred";
    private static final String OBJ1 = "obj1";
    private static final String DELETED = "deleted";
    private static final String ADDED = "added";

    private static ChangeLogEntry changeLogEntry;

    @BeforeAll
    static void beforeAll() {
        var baseGraph = GraphFactory.createDefaultGraph();
        baseGraph.add(
                Triple.create(
                        NodeFactory.createURI(SUB),
                        NodeFactory.createURI(PRED),
                        NodeFactory.createURI(OBJ1)));
        baseGraph.add(
                Triple.create(
                        NodeFactory.createURI(SUB),
                        NodeFactory.createURI(PRED),
                        NodeFactory.createURI(DELETED)));
        var delta = new DeltaCompressible(baseGraph);
        delta.add(
                Triple.create(
                        NodeFactory.createURI(SUB),
                        NodeFactory.createURI(PRED),
                        NodeFactory.createURI(ADDED)));
        delta.delete(
                Triple.create(
                        NodeFactory.createURI(SUB),
                        NodeFactory.createURI(PRED),
                        NodeFactory.createURI(DELETED)));

        var contextDeltas =
                List.of(
                        new ContextDelta(
                                "rdf",
                                new WeakReference<>(delta.getAdditions()),
                                new WeakReference<>(delta.getDeletions())));
        changeLogEntry = new ChangeLogEntry(MESSAGE, 1, contextDeltas);
        changeLogEntry.setChangeId(CHANGE_ID);
        changeLogEntry.setTimestamp(TIMESTAMP);
    }

    @Test
    void toDTO_changeLogEntry() {
        var dto = changeLogEntryMapper.toDTO(changeLogEntry);

        assertThat(dto.getContextDeltas()).hasSize(1);
        var contextDelta = dto.getContextDeltas().getFirst();
        var addition = assertDoesNotThrow(() -> contextDelta.getAdditions().getFirst());
        var deletion = assertDoesNotThrow(() -> contextDelta.getDeletions().getFirst());

        assertAll(
                () -> assertThat(dto.getChangeId()).isEqualTo(CHANGE_ID.toString()),
                () -> assertThat(LocalDateTime.parse(dto.getTimestamp())).isEqualTo(TIMESTAMP),
                () -> assertThat(dto.getMessage()).isEqualTo(MESSAGE),
                () -> assertThat(contextDelta.getContextName()).isEqualTo("rdf"),
                () -> assertThat(contextDelta.getAdditions()).hasSize(1),
                () -> assertThat(addition.getSubject()).isEqualTo(SUB),
                () -> assertThat(addition.getPredicate()).isEqualTo(PRED),
                () -> assertThat(addition.getObject()).isEqualTo(ADDED),
                () -> assertThat(contextDelta.getDeletions()).hasSize(1),
                () -> assertThat(deletion.getSubject()).isEqualTo(SUB),
                () -> assertThat(deletion.getPredicate()).isEqualTo(PRED),
                () -> assertThat(deletion.getObject()).isEqualTo(DELETED));
    }
}
