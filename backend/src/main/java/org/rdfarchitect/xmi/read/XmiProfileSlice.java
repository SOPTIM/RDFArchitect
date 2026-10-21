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

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out which classes belong to a profile.
 *
 * <p>A profile is the package and all its sub-packages, closed transitively over attribute types
 * and superclasses with the referenced classifiers copied in. The closure is load-bearing: with a
 * direct-children-only slice the CGMES 2.4.15 profiles collapse to three classes each and none
 * reproduces, because their attributes are typed with datatypes that live in a shared domain
 * package. Associations do not pull their other end in; only types and superclasses do.
 */
@UtilityClass
public class XmiProfileSlice {

    /**
     * The ids of every class a profile consists of.
     *
     * @param index the index the profile was read from
     * @param profilePackageId the package holding the profile
     */
    public Set<String> classIdsOf(XmiIndex index, String profilePackageId) {
        var slice = new LinkedHashSet<String>();
        var pending = new ArrayDeque<String>();
        for (var cimClass : index.getClasses().values()) {
            if (index.isWithin(cimClass.getPackageId(), profilePackageId)
                    && slice.add(cimClass.getId())) {
                pending.add(cimClass.getId());
            }
        }
        while (!pending.isEmpty()) {
            var cimClass = index.getClasses().get(pending.poll());
            if (cimClass == null) {
                continue;
            }
            for (var attribute : cimClass.getAttributes()) {
                pull(index, slice, pending, attribute.getTypeId());
            }
            for (var superClassId : cimClass.getSuperClassIds()) {
                pull(index, slice, pending, superClassId);
            }
        }
        return slice;
    }

    private void pull(
            XmiIndex index, Set<String> slice, ArrayDeque<String> pending, String classId) {
        if (classId != null && index.getClasses().containsKey(classId) && slice.add(classId)) {
            pending.add(classId);
        }
    }
}
