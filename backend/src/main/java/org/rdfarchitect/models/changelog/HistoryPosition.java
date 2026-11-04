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

import java.util.UUID;

/**
 * Where a workspace stands in its history: the change it stands on and the newest change recorded,
 * which lies further ahead when changes have been undone.
 *
 * <p>The log is a single line, so the two pin down everything a confirmation built from it showed:
 * a commit moves both, an undo or redo moves the first, and a change committed and undone again
 * elsewhere moves the second while leaving the first where it was.
 *
 * @param currentChangeId the change the workspace stands on, or {@code null} if there is none
 * @param newestChangeId the newest change recorded, undone or not, or {@code null} if there is none
 */
public record HistoryPosition(UUID currentChangeId, UUID newestChangeId) {}
