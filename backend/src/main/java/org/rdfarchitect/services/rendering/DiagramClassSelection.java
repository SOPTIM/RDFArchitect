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

package org.rdfarchitect.services.rendering;

import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Selects the classes a diagram of a single graph shows: the classes of a custom diagram, or the
 * classes of a package together with the classes outside of it they relate to. Shared by the
 * renderer and the diagram layout, so layout data is kept for exactly the classes a diagram can
 * show.
 */
public final class DiagramClassSelection {

    private static final String DEFAULT_PACKAGE = "default";

    private DiagramClassSelection() {}

    /**
     * The classes a diagram shows, keyed by their IRI.
     *
     * @param classes every class of the diagram
     * @param outsidePackageUris the IRIs of the classes that are only shown because a class of the
     *     package relates to them
     */
    public record SelectedClasses(Map<String, ICIMClass> classes, Set<String> outsidePackageUris) {}

    /**
     * Selects the classes a diagram shows under the given filter. With allowed UUIDs, these are the
     * classes of a custom diagram. Otherwise these are the classes of the package of the filter,
     * plus, if relations to external packages are included, the classes outside of it that are the
     * range of an association, a super class or a sub class of a class in the package.
     *
     * @param cimModel the graph the diagram belongs to
     * @param filter the filter the diagram is shown with
     * @return the selected classes
     */
    public static SelectedClasses select(ICIMModelFacade cimModel, GraphFilter filter) {
        var classes = new LinkedHashMap<String, ICIMClass>();

        if (filter.getAllowedUUIDs() != null) {
            for (var allowedUUID : filter.getAllowedUUIDs()) {
                var cimClass = cimModel.getCIMClass(UUID.fromString(allowedUUID));
                if (cimClass != null) {
                    classes.put(cimClass.getUri().toString(), cimClass);
                }
            }
            return new SelectedClasses(classes, Set.of());
        }

        var category = cimModel.getCIMClassCategory(resolvePackageUUID(filter));
        if (category == null) {
            return new SelectedClasses(classes, Set.of());
        }
        for (var cimClass : category.getClasses()) {
            classes.put(cimClass.getUri().toString(), cimClass);
        }

        if (!filter.isIncludeRelationsToExternalPackages()) {
            return new SelectedClasses(classes, Set.of());
        }

        var packageUris = Set.copyOf(classes.keySet());
        addExternallyRelatedClasses(filter, classes);
        var outsidePackageUris =
                classes.keySet().stream()
                        .filter(uri -> !packageUris.contains(uri))
                        .collect(Collectors.toSet());

        return new SelectedClasses(classes, outsidePackageUris);
    }

    private static UUID resolvePackageUUID(GraphFilter filter) {
        if (filter.getPackageUUID() == null || filter.getPackageUUID().equals(DEFAULT_PACKAGE)) {
            return null;
        }
        return UUID.fromString(filter.getPackageUUID());
    }

    private static void addExternallyRelatedClasses(
            GraphFilter filter, Map<String, ICIMClass> classes) {
        if (!filter.isIncludeAssociations() && !filter.isIncludeInheritance()) {
            return;
        }
        var classesInPackage = List.copyOf(classes.values());

        if (filter.isIncludeAssociations()) {
            for (var cimClass : classesInPackage) {
                for (var association : cimClass.getAssociations()) {
                    if (association.isRenderable()) {
                        addExternallyRelatedClass(classes, association.getRange());
                    }
                }
            }
        }

        if (filter.isIncludeInheritance()) {
            for (var cimClass : classesInPackage) {
                for (var superClass : cimClass.getSuperClasses()) {
                    addExternallyRelatedClass(classes, superClass);
                }
                for (var subClass : cimClass.getSubClasses()) {
                    addExternallyRelatedClass(classes, subClass);
                }
            }
        }
    }

    private static void addExternallyRelatedClass(
            Map<String, ICIMClass> classes, ICIMClass cimClass) {
        var uri = cimClass.getUri().toString();
        if (cimClass.getUuid() == null || classes.containsKey(uri)) {
            return;
        }
        classes.put(uri, cimClass);
    }
}
