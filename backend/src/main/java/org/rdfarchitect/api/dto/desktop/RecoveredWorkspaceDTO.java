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

package org.rdfarchitect.api.dto.desktop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A workspace the desktop app kept when it stopped with unsaved changes. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveredWorkspaceDTO {

    /** The file it is kept in, to address it by. */
    private String id;

    /** The name the workspace had. */
    private String workspaceName;

    /** When the copy was written. */
    private Instant savedAt;
}
