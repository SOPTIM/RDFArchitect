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

package org.rdfarchitect.services.readonly;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.database.DatabasePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadOnlyService
        implements IsReadOnlyUseCase, EnableEditingUseCase, DisableEditingUseCase {

    private final DatabasePort databasePort;

    @Override
    public boolean isReadOnly(String workspaceName) {
        return databasePort.isReadOnly(workspaceName);
    }

    @Override
    public void enableEditing(String workspaceName) {
        databasePort.enableEditing(workspaceName);
    }

    @Override
    public void disableEditing(String workspaceName) {
        databasePort.disableEditing(workspaceName);
    }
}
