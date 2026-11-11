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

/**
 * What an export may call something, given that the name goes into a URI verbatim.
 *
 * <p>Shared between reading and mapping because both have to agree on it: the reader drops what
 * cannot be named and the mapper builds the URIs, and a name one accepts and the other rejects
 * fails the whole schema it sits in.
 */
@UtilityClass
public class XmiNaming {

    /**
     * A name as a URI can hold it, or {@code null} when it cannot hold it at all.
     *
     * <p>Trailing whitespace is a defect of the export and is normalised: four attributes of the
     * CGMES 3.0 model are named {@code "lossPercentage "} and mean the name without it. Whitespace
     * in the middle cannot be normalised away — fourteen enum literals are named things like {@code
     * "TB 170"} and one package {@code "GeoSPARQL - Profile"} — so such a name is refused rather
     * than mangled into one that no longer says what the export says.
     */
    public String usableOrNull(String name) {
        if (name == null) {
            return null;
        }
        var trimmed = name.strip();
        return trimmed.isEmpty() || trimmed.chars().anyMatch(Character::isWhitespace)
                ? null
                : trimmed;
    }
}
