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
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

@UtilityClass
public class Multiplicities {

    private final String UNBOUNDED = "n";

    /**
     * Writes a multiplicity as {@code lower..upper} so that notations meaning the same compare
     * equal, {@code M:1} and {@code M:1..1} for instance. An open upper bound becomes {@code n}.
     *
     * @return the normalised multiplicity
     */
    public String normalize(CIMSMultiplicity multiplicity) {
        var bounds = CIMPropertyUtils.resolveMultiplicity(multiplicity.getUri().getSuffix());
        return format(bounds.lowerBound()) + ".." + format(bounds.upperBound());
    }

    private String format(Integer bound) {
        return bound == null ? UNBOUNDED : bound.toString();
    }
}
