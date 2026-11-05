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

import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiPackageScope;
import org.rdfarchitect.xmi.read.XmiPackageTree;
import org.rdfarchitect.xmi.read.XmiPackageTree.XmiPackageNode;
import org.rdfarchitect.xmi.read.XmiProfileSlice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which diagrams of an export a selection of packages would bring with it.
 *
 * <p>A diagram can be imported exactly when every class it draws is part of the import, so whether
 * it is available depends on the selection — and the selection is made before anything is written.
 * This works the dependence out once, at read time, in a form the caller can evaluate per tick
 * without asking again.
 *
 * <h2>The form</h2>
 *
 * For each class a diagram draws, the packages that would supply it: any one of them suffices,
 * because a package supplies every class of its schema. A diagram is available when the selection
 * meets every one of those groups.
 *
 * <p>The groups are shared rather than repeated per diagram. The CGMES 3.0 model draws 12235
 * diagram-to-class pairs out of 5852 classes, and between them those classes have only 592 distinct
 * supplier sets — written out per diagram the same answer costs 2.5 MB, shared it costs about 180
 * KB.
 */
@UtilityClass
public class XmiDiagramAvailability {

    /**
     * A diagram and what its availability depends on.
     *
     * @param packageId the package it is shown under, which is not always the one declaring it. See
     *     {@link #rowsByPackage}
     * @param supplierGroups indices into {@link Availability#supplierGroups()}, one per distinct
     *     group among the classes it draws. Empty means it draws no class and is never offered
     */
    public record DiagramDependence(
            String id, String packageId, String name, int classes, List<Integer> supplierGroups) {}

    /**
     * The packages that would supply one class, either of which is enough for it to be imported.
     *
     * <p>A package can be chosen with its sub-packages or alone, and the two differ in what they
     * supply, so each is named separately. A package supplying the class alone supplies it with its
     * sub-packages as well, and is therefore named in both.
     */
    public record SupplierGroup(List<String> packageIds, List<String> packageOnlyIds) {}

    /**
     * @param supplierGroups the distinct groups the diagrams refer to, by index
     */
    public record Availability(
            List<DiagramDependence> diagrams, List<SupplierGroup> supplierGroups) {}

    /** A selection of packages, as the two scopes it can be made in. */
    public record Selection(Set<String> withSubPackages, Set<String> packageOnly) {

        /** A selection taking every named package with its sub-packages. */
        public static Selection withSubPackages(Set<String> packageIds) {
            return new Selection(packageIds, Set.of());
        }
    }

    /** Works out what every diagram of the export depends on. */
    public Availability of(XmiIndex index) {
        var tree = XmiPackageTree.of(index);
        var suppliersByClass = suppliersByClass(index, tree);
        var rows = rowsByPackage(index, tree);
        var groupIndices = new LinkedHashMap<SupplierGroup, Integer>();
        var diagrams = new ArrayList<DiagramDependence>();

        for (var diagram : index.getDiagrams().values()) {
            var groups = new LinkedHashSet<Integer>();
            var drawn = 0;
            for (var shape : diagram.getShapes()) {
                var suppliers = suppliersByClass.get(shape.getClassId());
                if (suppliers == null) {
                    // A boundary, a note or a text: no class, so nothing to supply.
                    continue;
                }
                drawn++;
                groups.add(groupIndices.computeIfAbsent(suppliers, _ -> groupIndices.size()));
            }
            var row = rows.get(diagram.getOwningPackageId());
            // Without a row there is nowhere in the tree to show the diagram under, and one that
            // cannot be shown must not be offered either: it would be imported unseen.
            if (drawn > 0 && row != null) {
                diagrams.add(
                        new DiagramDependence(
                                diagram.getId(),
                                row,
                                nameOf(index, diagram.getOwningPackageId(), diagram.getName()),
                                drawn,
                                List.copyOf(groups)));
            }
        }
        return new Availability(List.copyOf(diagrams), List.copyOf(groupIndices.keySet()));
    }

    /**
     * The packages that would supply each class a diagram draws, by class. A package supplies every
     * class of its schema, which is its closure and not only what it declares: a diagram drawing a
     * datatype from elsewhere is available all the same, because the datatype comes along.
     *
     * <p>The closure is taken exactly as {@link XmiProfileMapper} takes it, down to leaving out the
     * header classes: a schema that is not written must not be promised here.
     *
     * <p>A class that describes a profile is left out of the question altogether. It becomes that
     * profile's ontology header and is never placed on a diagram, so requiring a supplier for it
     * would cost every CGMES 3.0 profile its own diagram: the only package whose schema holds it is
     * the one above the profile, taken whole, which is not what picking the profile selects.
     */
    private Map<String, SupplierGroup> suppliersByClass(XmiIndex index, List<XmiPackageNode> tree) {
        var headers = index.headerClassIds();
        var drawn = new LinkedHashSet<String>();
        index.getDiagrams()
                .values()
                .forEach(
                        diagram ->
                                diagram.getShapes()
                                        .forEach(
                                                shape -> {
                                                    if (index.getClasses()
                                                                    .containsKey(shape.getClassId())
                                                            && !headers.contains(
                                                                    shape.getClassId())) {
                                                        drawn.add(shape.getClassId());
                                                    }
                                                }));

        var withSubPackages = new LinkedHashMap<String, List<String>>();
        var packageOnly = new LinkedHashMap<String, List<String>>();
        for (var node : tree) {
            var ownHeaders = index.headerClassIdsOf(node.id());
            collect(
                    XmiProfileSlice.classIdsOf(
                            index, node.id(), XmiPackageScope.WITH_SUB_PACKAGES, ownHeaders),
                    drawn,
                    node.id(),
                    withSubPackages);
            collect(
                    XmiProfileSlice.classIdsOf(
                            index, node.id(), XmiPackageScope.PACKAGE_ONLY, ownHeaders),
                    drawn,
                    node.id(),
                    packageOnly);
        }

        var suppliers = new LinkedHashMap<String, SupplierGroup>();
        withSubPackages.forEach(
                (classId, packageIds) ->
                        suppliers.put(
                                classId,
                                new SupplierGroup(
                                        List.copyOf(packageIds),
                                        List.copyOf(
                                                packageOnly.getOrDefault(classId, List.of())))));
        return Map.copyOf(suppliers);
    }

    private void collect(
            Set<String> schema,
            Set<String> drawn,
            String packageId,
            Map<String, List<String>> suppliers) {
        for (var classId : drawn) {
            if (schema.contains(classId)) {
                suppliers.computeIfAbsent(classId, _ -> new ArrayList<>()).add(packageId);
            }
        }
    }

    /**
     * The package each one of the export's packages is shown under, which is where the diagrams it
     * declares belong.
     *
     * <p>Two kinds of package have no row to show a diagram under. One holding no class anywhere is
     * not offered as a schema and so is not reported at all — 194 of the CGMES 3.0 model's 926
     * diagrams are declared in one. And a profile is taken whole, so the packages within it are not
     * offered separately either, which is where another 73 sit. Both resolve outwards: to the
     * outermost profile around the package, or failing that to the nearest package that is offered.
     *
     * @return the row per package, which is the package itself wherever it has one of its own, and
     *     {@code null} where the package has neither a profile nor an offered package above it
     */
    private Map<String, String> rowsByPackage(XmiIndex index, List<XmiPackageNode> tree) {
        var offered = new LinkedHashSet<String>();
        tree.forEach(node -> offered.add(node.id()));
        var profiles = new LinkedHashSet<String>();
        index.getProfiles().forEach(profile -> profiles.add(profile.id()));

        var rows = new LinkedHashMap<String, String>();
        for (var packageId : index.getPackages().keySet()) {
            String nearestOffered = null;
            String outermostProfile = null;
            for (var at = packageId; at != null; at = parentOf(index, at)) {
                if (nearestOffered == null && offered.contains(at)) {
                    nearestOffered = at;
                }
                if (profiles.contains(at)) {
                    outermostProfile = at;
                }
            }
            rows.put(packageId, outermostProfile == null ? nearestOffered : outermostProfile);
        }
        return rows;
    }

    private String parentOf(XmiIndex index, String packageId) {
        var cimPackage = index.getPackages().get(packageId);
        return cimPackage == null ? null : cimPackage.getParentId();
    }

    private String nameOf(XmiIndex index, String owningPackageId, String stated) {
        if (stated != null && !stated.isBlank()) {
            return stated;
        }
        var owner = index.getPackages().get(owningPackageId);
        return owner == null ? "Diagram" : owner.getName();
    }

    /** Whether {@code selection} covers every group this diagram depends on. */
    public boolean isAvailable(
            DiagramDependence diagram, List<SupplierGroup> groups, Selection selection) {
        return !diagram.supplierGroups().isEmpty()
                && diagram.supplierGroups().stream()
                        .allMatch(group -> isMet(groups.get(group), selection));
    }

    private boolean isMet(SupplierGroup group, Selection selection) {
        return group.packageIds().stream().anyMatch(selection.withSubPackages()::contains)
                || group.packageOnlyIds().stream().anyMatch(selection.packageOnly()::contains);
    }
}
