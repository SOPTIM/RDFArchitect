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

package org.rdfarchitect.services.select;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Document structure shared by the profile documentation exports. It decides which class is
 * documented in which section and how the inheritance chain of a class is resolved, so that the
 * HTML and the AsciiDoc export always describe the same document.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProfileDocumentationStructure {

    /** Stereotypes that get their own section, in the order the sections appear. */
    public static final List<String> STEREOTYPES =
            List.of(
                    CIMStereotypes.shortCircuitString,
                    CIMStereotypes.descriptionString,
                    CIMStereotypes.operationString,
                    CIMStereotypes.europeanString,
                    CIMStereotypes.entsoeString);

    /**
     * A section of the documentation.
     *
     * @param title heading of the section
     * @param stereotype stereotype all classes of the section share, {@code null} for the abstract
     *     and the remaining classes section
     * @param classes classes documented in this section
     */
    public record Section(String title, String stereotype, List<ClassUMLAdaptedDTO> classes) {}

    /**
     * Groups the classes into the sections of the documentation: one section per known stereotype,
     * followed by the abstract classes and finally everything that is left.
     */
    public static List<Section> buildSections(List<ClassUMLAdaptedDTO> classList) {
        var sections = new ArrayList<Section>();
        var processedUuids = new HashSet<UUID>();

        for (var stereotype : STEREOTYPES) {
            var stereotypeClasses =
                    classList.stream()
                            .filter(
                                    c ->
                                            c.getStereotypes() != null
                                                    && c.getStereotypes().contains(stereotype)
                                                    && !processedUuids.contains(c.getUuid()))
                            .toList();
            if (!stereotypeClasses.isEmpty()) {
                sections.add(
                        new Section("Classes (" + stereotype + ")", stereotype, stereotypeClasses));
                markProcessed(stereotypeClasses, processedUuids);
            }
        }

        var abstractClasses =
                classList.stream()
                        .filter(c -> !isConcrete(c) && !processedUuids.contains(c.getUuid()))
                        .toList();
        if (!abstractClasses.isEmpty()) {
            sections.add(new Section("Abstract Classes", null, abstractClasses));
            markProcessed(abstractClasses, processedUuids);
        }

        var remainingClasses =
                classList.stream().filter(c -> !processedUuids.contains(c.getUuid())).toList();
        if (!remainingClasses.isEmpty()) {
            sections.add(new Section("Classes", null, remainingClasses));
        }

        return sections;
    }

    private static void markProcessed(
            List<ClassUMLAdaptedDTO> classes, HashSet<UUID> processedUuids) {
        classes.stream()
                .filter(c -> c.getUuid() != null)
                .forEach(c -> processedUuids.add(c.getUuid()));
    }

    public static boolean isConcrete(ClassUMLAdaptedDTO classUMLAdaptedDTO) {
        return hasStereotype(classUMLAdaptedDTO, CIMStereotypes.concreteString);
    }

    public static boolean isEnumeration(ClassUMLAdaptedDTO classUMLAdaptedDTO) {
        return hasStereotype(classUMLAdaptedDTO, CIMStereotypes.enumerationString);
    }

    private static boolean hasStereotype(ClassUMLAdaptedDTO classUMLAdaptedDTO, String stereotype) {
        return classUMLAdaptedDTO.getStereotypes() != null
                && classUMLAdaptedDTO.getStereotypes().contains(stereotype);
    }

    /**
     * Walks up the super class chain of the given class and returns the resolvable ancestors,
     * closest ancestor first. Unresolvable super classes end the chain, cycles are broken.
     */
    public static List<ClassUMLAdaptedDTO> resolveAncestorChain(
            ClassUMLAdaptedDTO classUMLAdaptedDTO, List<ClassUMLAdaptedDTO> classList) {
        var ancestors = new ArrayList<ClassUMLAdaptedDTO>();
        var visited = new HashSet<String>();
        visited.add(classUMLAdaptedDTO.getPrefix() + "#" + classUMLAdaptedDTO.getLabel());

        var currentSuperClass = classUMLAdaptedDTO.getSuperClass();
        while (currentSuperClass != null) {
            var key = currentSuperClass.getPrefix() + "#" + currentSuperClass.getLabel();
            if (!visited.add(key)) {
                break;
            }

            var resolved = findClassByLabel(currentSuperClass, classList);
            if (resolved == null) {
                break;
            }

            ancestors.add(resolved);
            currentSuperClass = resolved.getSuperClass();
        }

        return ancestors;
    }

    private static ClassUMLAdaptedDTO findClassByLabel(
            ClassUMLAdaptedDTO superClass, List<ClassUMLAdaptedDTO> classList) {
        return classList.stream()
                .filter(c -> c.getLabel() != null && c.getLabel().equals(superClass.getLabel()))
                .filter(
                        c ->
                                superClass.getPrefix() == null
                                        || superClass.getPrefix().equals(c.getPrefix()))
                .findFirst()
                .orElse(null);
    }
}
