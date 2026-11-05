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

package org.rdfarchitect.models.changelog;

/**
 * The outcome of one step through the history: what was taken back or reapplied, and where that
 * leaves the workspace.
 *
 * <p>The two flags are answered while the step still holds the lock. Asked afterwards they would
 * cost two more round trips for the editor and could, between them, describe a workspace that
 * another request had meanwhile moved on.
 *
 * @param change the commit that was undone or redone
 * @param canUndo whether there is still something to undo
 * @param canRedo whether there is something to redo
 */
public record WorkspaceHistoryStep(
        WorkspaceChangeLogEntry change, boolean canUndo, boolean canRedo) {}
