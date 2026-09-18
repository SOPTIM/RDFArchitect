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

package org.rdfarchitect.services.validation.workspace.rule;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;

@UtilityClass
public class Multiplicities {

    private final String PREFIX = "M:";

    private final String SEPARATOR = "..";

    private final String UNBOUNDED = "n";

    /**
     * Writes a multiplicity as {@code lower..upper} so that notations meaning the same compare
     * equal, {@code M:1} and {@code M:1..1} for instance. An open upper bound stays {@code n}.
     *
     * <p>A notation that is not {@code M:lower..upper} is handed back as it stands rather than
     * guessed at, so that two different ones never come out the same. This is also why the bounds
     * are read here instead of through {@link
     * org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils#resolveMultiplicity(String)},
     * which turns everything it cannot parse into the open bound.
     *
     * @return the normalised multiplicity
     */
    public String normalize(CIMSMultiplicity multiplicity) {
        var notation = multiplicity.getUri().getSuffix();
        if (!notation.startsWith(PREFIX)) {
            return notation;
        }
        var bounds = notation.substring(PREFIX.length());
        var separator = bounds.indexOf(SEPARATOR);
        var lower = separator < 0 ? bounds : bounds.substring(0, separator);
        var upper = separator < 0 ? bounds : bounds.substring(separator + SEPARATOR.length());
        return isBound(lower) && isBound(upper) ? lower + SEPARATOR + upper : notation;
    }

    private boolean isBound(String bound) {
        return UNBOUNDED.equals(bound)
                || (!bound.isEmpty() && bound.chars().allMatch(Character::isDigit));
    }
}
