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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The packages of an export, as the choice of what to import is made over them.
 *
 * <p>Every package can become a schema, whether or not it declares a profile. The CGMES 3.0 full
 * model holds 39 profiles and 3625 classes besides them — the IEC base model and the European
 * extensions — which no profile claims and which no choice could otherwise reach.
 *
 * <h2>Why the counts are the real ones</h2>
 *
 * A package is not written as the classes it declares but as those closed over its attribute types
 * and superclasses, so the two numbers differ and the difference is not small: a CGMES 2.4.15
 * profile of ten classes pulls in another ten datatypes. Offering the declared count alone would
 * promise one thing and import another, so both are reported and the caller can say which is which.
 *
 * <p>Both shapes a schema can take are counted: the package with its sub-packages, which is what a
 * profile is, and the package alone, which is what a container's own classes become when its
 * sub-packages are chosen separately.
 *
 * <p>They cost one closure per package and shape. The classes a package declares are worked out for
 * every package in a single walk and the closure is run over that, rather than asking the index per
 * package, which would walk the ancestry of every class once per package.
 */
@UtilityClass
public class XmiPackageTree {

    /**
     * One package of an export.
     *
     * @param parentId the package containing this one, {@code null} for an outermost one
     * @param profile whether the package declares a profile, which is what gives a schema made from
     *     it a namespace of its own
     * @param namespaceCandidates the profile URIs its metadata classes declare, empty for a package
     *     that is no profile
     * @param declaredClasses the classes in the package and its sub-packages
     * @param dependencyClasses what the closure adds on top of those
     * @param ownClassIds the classes the package declares itself, its sub-packages left out, by
     *     {@code xmi:id}. Listed rather than counted because a caller showing what a schema holds
     *     has to name them; what the package holds with its sub-packages is the union of these down
     *     the tree, and is not repeated here. By id and not by name, so that the two classes an
     *     export may both call {@code String} stay two
     * @param ownDependencyClassIds what the closure adds on top of those, the same way
     */
    public record XmiPackageNode(
            String id,
            String parentId,
            String name,
            boolean profile,
            List<String> namespaceCandidates,
            int declaredClasses,
            int dependencyClasses,
            List<String> ownClassIds,
            List<String> ownDependencyClassIds) {}

    /**
     * Every package that would make a schema, in the order the model declares them.
     *
     * <p>A package that would write nothing is left out: no class anywhere below it and no header
     * of its own. An export carries plenty of those. One that declares only a header is kept — the
     * header is what it has to say, and a schema of just that is still a schema.
     */
    public List<XmiPackageNode> of(XmiIndex index) {
        var declaredByPackage = declaredClassesByPackage(index);
        var ownByPackage = ownClassesByPackage(index);
        var profilesByPackage = new LinkedHashMap<String, XmiProfile>();
        index.getProfiles().forEach(profile -> profilesByPackage.put(profile.id(), profile));

        var nodes = new ArrayList<XmiPackageNode>();
        for (var cimPackage : index.getPackages().values()) {
            var declared = declaredByPackage.getOrDefault(cimPackage.getId(), Set.of());
            var profile = profilesByPackage.get(cimPackage.getId());
            // The header class is no part of the schema and no starting point of its closure, so
            // the counts have to leave it out the way the import does.
            var headers = profile == null ? Set.<String>of() : index.headerClassIdsOf(profile);
            var declaredWithoutHeader = without(declared, headers);
            var slice = XmiProfileSlice.closureOf(index, declaredWithoutHeader);
            var own = without(ownByPackage.getOrDefault(cimPackage.getId(), Set.of()), headers);
            var ownSlice = own.isEmpty() ? own : XmiProfileSlice.closureOf(index, own);
            if (declaredWithoutHeader.isEmpty() && headers.isEmpty()) {
                continue;
            }
            nodes.add(
                    new XmiPackageNode(
                            cimPackage.getId(),
                            cimPackage.getParentId(),
                            cimPackage.getName(),
                            profile != null,
                            profile == null ? List.of() : profile.namespaceCandidates(),
                            declaredWithoutHeader.size(),
                            slice.size() - declaredWithoutHeader.size(),
                            byName(index, own),
                            byName(index, without(ownSlice, own))));
        }
        return nodes;
    }

    /** The class ids in the order a caller would list them, which is by the name they carry. */
    private List<String> byName(XmiIndex index, Set<String> classIds) {
        return classIds.stream()
                .filter(classId -> index.getClasses().containsKey(classId))
                .sorted(Comparator.comparing(classId -> nameOf(index, classId)))
                .toList();
    }

    private String nameOf(XmiIndex index, String classId) {
        var name = index.getClasses().get(classId).getName();
        return name == null ? "" : name;
    }

    private Set<String> without(Set<String> classes, Set<String> excluded) {
        if (excluded.isEmpty()) {
            return classes;
        }
        var kept = new LinkedHashSet<>(classes);
        kept.removeAll(excluded);
        return kept;
    }

    /** The classes every package declares itself, its sub-packages left out. */
    private Map<String, Set<String>> ownClassesByPackage(XmiIndex index) {
        var own = new LinkedHashMap<String, Set<String>>();
        for (var cimClass : index.getClasses().values()) {
            own.computeIfAbsent(cimClass.getPackageId(), _ -> new LinkedHashSet<>())
                    .add(cimClass.getId());
        }
        return own;
    }

    /**
     * The classes every package holds, counting those of its sub-packages as its own. One walk up
     * the ancestry per class, rather than one walk over every class per package.
     */
    private Map<String, Set<String>> declaredClassesByPackage(XmiIndex index) {
        var declared = new LinkedHashMap<String, Set<String>>();
        for (var cimClass : index.getClasses().values()) {
            for (var ancestor : index.ancestryOf(cimClass.getPackageId())) {
                declared.computeIfAbsent(ancestor.getId(), _ -> new LinkedHashSet<>())
                        .add(cimClass.getId());
            }
        }
        return declared;
    }
}
