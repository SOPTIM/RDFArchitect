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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateEnumEntriesService implements ReplaceOrCreateEnumEntryUseCase {

    private final DatabasePort databasePort;
    private final EnumEntryMapper mapper;

    @Override
    public UUID replaceOrCreateEnumEntry(
            GraphIdentifier graphIdentifier, EnumEntryDTO enumEntryDTO) {
        String message;
        UUID uuid;

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            if (enumEntryDTO.getLabel() == null || enumEntryDTO.getLabel().trim().isEmpty()) {
                throw new IllegalArgumentException("New enum entry label cannot be null or empty");
            }

            var cimEnumEntry = mapper.toCIMObject(enumEntryDTO);
            if (enumEntryDTO.getUuid() == null) {
                uuid = UUID.randomUUID();
                cimEnumEntry.setUuid(uuid);
                CIMUpdates.insertEnumEntry(
                        graph,
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        cimEnumEntry);
                message = "Created enum entry \"%s\" (%s)".formatted(cimEnumEntry.getLabel(), uuid);
            } else {
                uuid = enumEntryDTO.getUuid();
                CIMUpdates.replaceEnumEntry(
                        graph,
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                        cimEnumEntry);
                message =
                        "Replaced enum entry \"%s\" (%s)".formatted(cimEnumEntry.getLabel(), uuid);
            }

            ctx.commit(message);
        }

        return uuid;
    }
}
