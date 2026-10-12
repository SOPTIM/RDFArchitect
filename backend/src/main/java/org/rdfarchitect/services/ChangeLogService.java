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
import org.rdfarchitect.database.DatabasePort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChangeLogService implements ChangeLogUseCase {

    private final ChangeLogEntryMapper mapper;
    private final DatabasePort databasePort;

    @Override
    public List<ChangeLogEntryDTO> listChanges(String workspaceName) {
        return mapper.toDTOList(databasePort.listChanges(workspaceName));
    }

    @Override
    public ChangeLogEntryDTO pendingUndo(String workspaceName) {
        var pending = databasePort.pendingUndo(workspaceName);
        return pending == null ? null : mapper.toDTO(pending);
    }
}
