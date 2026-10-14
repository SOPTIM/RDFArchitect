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

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything one Enterprise Architect export holds, as far as an import reads it. */
@Getter
public class XmiIndex {

    private final Map<String, XmiPackage> packages = new LinkedHashMap<>();

    private final Map<String, XmiClass> classes = new LinkedHashMap<>();

    private final List<XmiAssociationEnd> associationEnds = new ArrayList<>();

    /** The packages that hold a profile, in the order the model declares them. */
    private final List<XmiProfile> profiles = new ArrayList<>();

    /**
     * Every package from {@code packageId} up to the outermost one, nearest first.
     *
     * @return an empty list when the package is unknown, which a partial export makes possible
     */
    public List<XmiPackage> ancestryOf(String packageId) {
        var ancestry = new ArrayList<XmiPackage>();
        var current = packages.get(packageId);
        while (current != null && !ancestry.contains(current)) {
            ancestry.add(current);
            current = packages.get(current.getParentId());
        }
        return ancestry;
    }

    /** Whether {@code packageId} is {@code ancestorId} or sits anywhere below it. */
    public boolean isWithin(String packageId, String ancestorId) {
        return ancestryOf(packageId).stream()
                .anyMatch(ancestor -> ancestor.getId().equals(ancestorId));
    }
}
