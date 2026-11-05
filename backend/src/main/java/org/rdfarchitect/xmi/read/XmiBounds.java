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

package org.rdfarchitect.xmi.read;

import lombok.experimental.UtilityClass;

/** Normalises the two defective ways an export states a bound. */
@UtilityClass
class XmiBounds {

    /** How RDFArchitect and the releases spell an unbounded upper bound. */
    private final String UNBOUNDED = "n";

    /**
     * Takes both spellings of the unbounded upper bound. {@code *} is the normal one; {@code n}
     * occurs only in the CGMES 3.0 full model and never in 2.4.15, so a reader that expects an
     * integer besides {@code *} breaks on it.
     */
    String normaliseUpperBound(String value) {
        if (value == null) {
            return null;
        }
        var bound = value.strip();
        return "*".equals(bound) || UNBOUNDED.equals(bound) ? UNBOUNDED : bound;
    }

    /**
     * Takes a connector end's multiplicity as it stands, but for the unbounded upper bound. It is
     * not expanded into two bounds, because a release keeps {@code M:1} and {@code M:1..1} apart.
     *
     * <p>A range that states no upper bound at all — {@code 1..} occurs once in each full model —
     * keeps the bound it does state, which is the only reading the file supports.
     */
    String normaliseMultiplicity(String value) {
        if (value == null) {
            return null;
        }
        var multiplicity = value.strip();
        if (multiplicity.endsWith("..")) {
            multiplicity = multiplicity.substring(0, multiplicity.length() - 2);
        }
        var separator = multiplicity.indexOf("..");
        if (separator < 0) {
            return normaliseUpperBound(multiplicity);
        }
        return multiplicity.substring(0, separator)
                + ".."
                + normaliseUpperBound(multiplicity.substring(separator + 2));
    }
}
