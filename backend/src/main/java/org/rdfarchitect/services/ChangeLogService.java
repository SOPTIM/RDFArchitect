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

package org.rdfarchitect.services;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.ChangeLogEntryDTO;
import org.rdfarchitect.api.dto.ChangeLogEntryMapper;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.changelog.GraphChangeLog;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChangeLogService implements ChangeLogUseCase {

    private final ChangeLogEntryMapper mapper;

    private final Map<String, Map<String, Map<String, GraphChangeLog>>> changeLogs = new ConcurrentHashMap<>();

    private GraphChangeLog getChangeLog(GraphIdentifier graphIdentifier) {
        return changeLogs
                  .getOrDefault(SessionContext.getSessionId(), Collections.emptyMap())
                  .getOrDefault(graphIdentifier.getDatasetName(), Collections.emptyMap())
                  .getOrDefault(graphIdentifier.getGraphUri(), new GraphChangeLog());
    }

    @Override
    public void recordChange(GraphIdentifier graphIdentifier, ChangeLogEntry entry) {
        var graphChangeLog = changeLogs
                  .computeIfAbsent(SessionContext.getSessionId(), _ -> new ConcurrentHashMap<>())
                  .computeIfAbsent(graphIdentifier.getDatasetName(), _ -> new ConcurrentHashMap<>())
                  .computeIfAbsent(graphIdentifier.getGraphUri(), _ -> new GraphChangeLog());
        graphChangeLog.addEntry(entry);
    }

    @Override
    public void undoChange(GraphIdentifier graphIdentifier) {
        getChangeLog(graphIdentifier).undoChange();
    }

    @Override
    public void redoChange(GraphIdentifier graphIdentifier) {
        getChangeLog(graphIdentifier).redoChange();
    }

    @Override
    public List<ChangeLogEntryDTO> listChanges(GraphIdentifier graphIdentifier) {
        return mapper.toDTOList(getChangeLog(graphIdentifier).getEntries());
    }

    @Override
    public void restoreVersion(GraphIdentifier graphIdentifier, UUID versionId) {
        getChangeLog(graphIdentifier).restore(versionId);
    }
}
