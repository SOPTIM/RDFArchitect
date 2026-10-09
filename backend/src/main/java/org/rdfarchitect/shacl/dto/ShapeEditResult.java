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

package org.rdfarchitect.shacl.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** The document after a form edit, with anything the user should know about what changed. */
@Data
@Builder
public class ShapeEditResult {

    private String turtle;

    /**
     * What the edit cost beyond the change itself — a comment inside the rewritten shape, say.
     *
     * <p>Only the edited statement is rewritten, so the rest of the document keeps its comments and
     * layout byte for byte; these warnings are about the one statement that could not.
     */
    private List<String> warnings;
}
