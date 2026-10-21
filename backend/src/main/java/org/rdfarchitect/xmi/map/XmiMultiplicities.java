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

package org.rdfarchitect.xmi.map;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

/**
 * What a multiplicity becomes, which is an IRI in the {@code cims} namespace spelling {@code M:}
 * and the bounds — {@code M:0..n}, {@code M:1..1}, {@code M:1}.
 *
 * <p>An association's is taken as the export spells it rather than expanded into two bounds,
 * because a release keeps {@code M:1} and {@code M:1..1} apart and both occur. An attribute's is
 * built from its two bounds, which is the only form the model part states.
 */
@UtilityClass
class XmiMultiplicities {

    private final String NOTATION = "M:";

    /** The multiplicity an attribute's two bounds make, defaulting to {@code 1..1} as UML does. */
    CIMSMultiplicity ofBounds(String lower, String upper) {
        return multiplicity(bound(lower) + ".." + bound(upper));
    }

    /**
     * The multiplicity a connector end states, verbatim. A malformed one — a released export writes
     * {@code "1.."} once — is not repaired into something it might have meant.
     */
    CIMSMultiplicity ofEnd(String stated) {
        return stated == null || stated.isBlank() ? null : multiplicity(stated.strip());
    }

    private CIMSMultiplicity multiplicity(String bounds) {
        return new CIMSMultiplicity(CIMS.namespace + NOTATION + bounds);
    }

    private String bound(String value) {
        return value == null || value.isBlank() ? "1" : value.strip();
    }
}
