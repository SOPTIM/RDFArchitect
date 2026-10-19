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

package org.rdfarchitect.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ContextDeltaDTO {
    private String contextName;
    private String graphUri;

    /** The triples the change added and removed; {@code null} for data that is not held as RDF. */
    private List<TripleDTO> additions;

    private List<TripleDTO> deletions;

    /**
     * What the change did to data held as named values rather than as triples — the namespace
     * prefixes, the colours the schemas are drawn in. Empty wherever {@link #getAdditions()} and
     * {@link #getDeletions()} answer, since a participant is one or the other.
     */
    private List<ValueChangeDTO> values;
}
