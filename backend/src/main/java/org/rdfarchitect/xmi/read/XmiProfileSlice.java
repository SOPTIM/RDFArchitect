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
 * Works out which classes belong to a package written as a schema.
 *
 * <p>A schema is the package and all its sub-packages, closed transitively over attribute types and
 * superclasses with the referenced classifiers copied in. The closure is load-bearing: with a
 * direct-children-only slice the CGMES 2.4.15 profiles collapse to three classes each and none
 * reproduces, because their attributes are typed with datatypes that live in a shared domain
 * package. Associations do not pull their other end in; only types and superclasses do.
 *
 * <p>Nothing here is particular to a profile. A profile is a package that also declares a namespace
 * of its own; which classes it consists of is decided the same way as for any other.
 */
@UtilityClass
public class XmiProfileSlice {

    /**
     * The ids of every class a package consists of as a schema, its sub-packages included.
     *
     * @param index the index the package was read from
     * @param packageId the package to start from
     */
    public Set<String> classIdsOf(XmiIndex index, String packageId) {
        return classIdsOf(index, packageId, XmiPackageScope.WITH_SUB_PACKAGES, Set.of());
    }

    /**
     * The ids of every class a package consists of as a schema.
     *
     * @param excluded classes the package declares that are not part of the schema. They are no
     *     starting point of the closure either: what they refer to describes them and not the
     *     schema
     */
    public Set<String> classIdsOf(
            XmiIndex index, String packageId, XmiPackageScope scope, Set<String> excluded) {
        var declared = new LinkedHashSet<String>();
        for (var cimClass : index.getClasses().values()) {
            if (declares(index, packageId, cimClass, scope)
                    && !excluded.contains(cimClass.getId())) {
                declared.add(cimClass.getId());
            }
        }
        return closureOf(index, declared);
    }

    private boolean declares(
            XmiIndex index, String packageId, XmiClass cimClass, XmiPackageScope scope) {
        return scope == XmiPackageScope.PACKAGE_ONLY
                ? packageId.equals(cimClass.getPackageId())
                : index.isWithin(cimClass.getPackageId(), packageId);
    }

    /**
     * The same closure from a set of classes that is already known, for a caller that works out
     * what a package declares more cheaply than by asking per package.
     *
     * @param declared the classes to start from, which are part of the result
     */
    public Set<String> closureOf(XmiIndex index, Set<String> declared) {
        var slice = new LinkedHashSet<String>(declared);
        var pending = new ArrayDeque<>(declared);
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
