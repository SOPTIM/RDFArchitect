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

/**
 * Every term a workspace's schema declares, for an editor to complete against.
 *
 * <p>Sent whole rather than queried per keystroke. A workspace's schema changes only when the
 * schema is edited, so the client can hold this and filter it locally, which is the difference
 * between completion that appears as you type and completion that waits for a round trip.
 */
@Data
@Builder
public class SchemaTerms {

    /**
     * The profiles the terms were collected from, matching a validation report's {@code profiles}.
     */
    private List<String> profiles;

    private List<SchemaTerm> terms;
}
