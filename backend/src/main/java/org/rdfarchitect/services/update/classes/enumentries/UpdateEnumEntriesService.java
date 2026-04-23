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

package org.rdfarchitect.services.update.classes.enumentries;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.TxnType;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateEnumEntriesService implements ReplaceOrCreateEnumEntryUseCase {

    private final DatabasePort databasePort;
    private final EnumEntryMapper mapper;
    private final ChangeLogUseCase changeLogUseCase;

    @Override
    public UUID replaceOrCreateEnumEntry(
            GraphIdentifier graphIdentifier, EnumEntryDTO enumEntryDTO) {
        GraphRewindable graph = null;
        String message;
        UUID uuid;

        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);

            if (enumEntryDTO.getLabel() == null || enumEntryDTO.getLabel().trim().isEmpty()) {
                throw new IllegalArgumentException("New enum entry label cannot be null or empty");
            }

            var cimEnumEntry = mapper.toCIMObject(enumEntryDTO);
            if (enumEntryDTO.getUuid() == null) {
                uuid = UUID.randomUUID();
                cimEnumEntry.setUuid(uuid);
                CIMUpdates.insertEnumEntry(
                        graph,
                        databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                        cimEnumEntry);
                message = "Enum entry " + uuid + " created";
            } else {
                uuid = enumEntryDTO.getUuid();
                CIMUpdates.replaceEnumEntry(
                        graph,
                        databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                        cimEnumEntry);
                message = "Enum entry " + uuid + " replaced";
            }

            graph.commit();
        } catch (Exception e) {
            if (graph != null) {
                graph.abort();
            }
            throw e;
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        changeLogUseCase.recordChange(
                graphIdentifier, new ChangeLogEntry(message, graph.getLastDelta()));
        return uuid;
    }
}
