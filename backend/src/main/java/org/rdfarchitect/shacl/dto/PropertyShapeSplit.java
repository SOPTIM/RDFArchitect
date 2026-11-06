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

import lombok.Data;

/**
 * Changing a shared rule for one shape only, by giving that shape a copy of it.
 *
 * <p>The alternative to editing a rule forty classes rely on. The copy is taken from the rule's own
 * text, so it starts out saying exactly what the original says — comments and layout included — and
 * only then is the edit applied to it.
 */
@Data
public class PropertyShapeSplit {

    /** The name the copy is written under. Must not be a subject the document already writes. */
    private String newIri;

    /**
     * The node shape whose reference is moved to the copy. Every other shape keeps the original.
     */
    private String nodeShapeIri;

    /**
     * Which of that shape's rules is being repointed, as {@link
     * PropertyShapeModel#getSourceIndex()} gave it.
     *
     * <p>Needed because one node shape may reference the same rule twice, and only the reference
     * the edit was made on moves to the copy.
     */
    private Integer sourceIndex;
}
