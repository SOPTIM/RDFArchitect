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

package org.rdfarchitect.services.versioncontrol;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionControlService implements CanRedoUseCase, CanUndoUseCase, RedoUseCase, UndoUseCase, PersistUseCase, RestoreVersionUseCase {

    private final DatabasePort databasePort;
    private final DatabaseConnection databaseConnection;
    private final ChangeLogUseCase changelogUseCase;

    @Override
    public Boolean canRedo(GraphIdentifier graphIdentifier) {
        return databasePort.canRedo(graphIdentifier);
    }

    @Override
    public Boolean canUndo(GraphIdentifier graphIdentifier) {
        return databasePort.canUndo(graphIdentifier);
    }

    @Override
    public void redo(GraphIdentifier graphIdentifier) {
        databasePort.redo(graphIdentifier);
        changelogUseCase.redoChange(graphIdentifier);
    }

    @Override
    public void undo(GraphIdentifier graphIdentifier) {
        databasePort.undo(graphIdentifier);
        changelogUseCase.undoChange(graphIdentifier);
    }

    @Override
    public void persist(GraphIdentifier graphIdentifier) {
        databasePort.persist(databaseConnection, graphIdentifier);
    }

    @Override
    public void restoreVersion(GraphIdentifier graphIdentifier, UUID versionId) {
        databasePort.restore(graphIdentifier, versionId);
        changelogUseCase.restoreVersion(graphIdentifier, versionId);
    }
}
