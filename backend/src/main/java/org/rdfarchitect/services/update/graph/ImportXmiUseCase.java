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

package org.rdfarchitect.services.update.graph;

import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Imports schemas from an Enterprise Architect XMI export. */
public interface ImportXmiUseCase {

    /**
     * A package one of the uploaded exports holds, which can be imported as a schema.
     *
     * <p>Reported as a flat list rather than nested: {@code parentId} is what makes it a tree, and
     * a package is named once however deep it sits.
     *
     * @param fileIndex which of the exports it was found in, by its position in the progress
     * @param fileName the export the package was found in
     * @param id the package's {@code xmi:id}, which addresses it for a later import
     * @param parentId the package containing this one, {@code null} for an outermost one
     * @param name the package's name
     * @param profile whether it declares a profile, which is what gives a schema made from it a
     *     namespace of its own
     * @param namespaceCandidates the profile URIs its metadata classes declare, empty for a package
     *     that is no profile
     * @param declaredClasses the classes in the package and its sub-packages
     * @param dependencyClasses the classes its attribute types and superclasses require on top of
     *     those; both numbers end up in the schema and only their sum says how big it is
     * @param ownClasses the classes the package declares itself, its sub-packages left out, by
     *     their position in {@link ImportOutcome.PackagesScanned#classNames()}
     * @param ownDependencies what the closure adds on top of those, the same way. The two own lists
     *     are what a container's own classes become when its sub-packages are chosen separately,
     *     and between them they name every class of the export exactly once: what a package holds
     *     with its sub-packages is the union of them down the tree
     * @param modelNamespace what the export names nearly every resource with, {@code null} when it
     *     states none
     * @param extensionNamespace what the export names its extension-stereotyped elements with,
     *     {@code null} when it states none and one has to be supplied with the import
     * @param hasExtensionElements whether the export holds anything the extension namespace would
     *     name, which is what makes asking for one worthwhile. All three describe the export rather
     *     than the package, repeated per package because that is what the caller has in hand
     */
    record ScannedPackage(
            int fileIndex,
            String fileName,
            String id,
            String parentId,
            String name,
            boolean profile,
            List<String> namespaceCandidates,
            int declaredClasses,
            int dependencyClasses,
            List<Integer> ownClasses,
            List<Integer> ownDependencies,
            String modelNamespace,
            String extensionNamespace,
            boolean hasExtensionElements) {}

    /**
     * One entry of an export's namespace prefix table.
     *
     * @param fileIndex which of the exports it was found in
     * @param prefix what the export abbreviates the namespace to
     * @param namespace what the prefix stands for
     */
    record ScannedPrefix(int fileIndex, String prefix, String namespace) {}

    /**
     * A class diagram of an export, and what its availability depends on.
     *
     * <p>A diagram can be imported exactly when every class it draws is part of the import, which
     * the caller decides package by package. {@code supplierGroups} is that dependence made
     * evaluable: one group per distinct set of packages that could supply a class it draws, and the
     * diagram is available when the selection meets every group.
     *
     * @param fileIndex which of the exports it was found in, by its position in the progress
     * @param id the diagram's {@code xmi:id}, which is how the import names it
     * @param packageId the package it is offered under. A package holding no class is not offered
     *     as a schema and a profile is offered whole, so a diagram declared in either is offered
     *     under the nearest package that does have a row of its own
     * @param classes how many classes it draws
     * @param supplierGroups indices into {@link ImportOutcome.PackagesScanned#supplierGroups()}
     */
    record ScannedDiagram(
            int fileIndex,
            String id,
            String packageId,
            String name,
            int classes,
            List<Integer> supplierGroups) {}

    /**
     * One set of packages any of which would supply a class, shared between the diagrams that refer
     * to it.
     *
     * @param fileIndex which of the exports it belongs to
     * @param index its position among the groups of that export
     * @param packageIds the packages supplying the class when taken with their sub-packages
     * @param packageOnlyIds those among them that supply it taken alone, the two scopes a package
     *     can be chosen in differing in what they supply
     */
    record SupplierGroup(
            int fileIndex, int index, List<String> packageIds, List<String> packageOnlyIds) {}

    /**
     * Reads every uploaded export and reports the packages it holds. Nothing is stored: which of
     * them become schemas is a later decision.
     *
     * @param files the uploaded exports
     * @param listener receives the progress of the read and can cancel it
     * @return the packages found, in the order the exports declare them, together with the class
     *     diagrams and what their availability depends on
     */
    ImportOutcome.PackagesScanned scanPackages(
            List<MultipartFile> files, ImportProgressListener listener);

    /**
     * Builds a schema from each chosen package and stores it, diagrams included.
     *
     * <p>The exports are the ones a previous {@link #scanPackages} read, which is what makes the
     * choice addressable: a selection names a package by the file it was found in and the id the
     * read reported for it. An export is read in full once here, however many of its packages were
     * chosen.
     *
     * @param datasetName the workspace the schemas go into, created if it does not exist
     * @param files the exports the selection refers to, in the order they were read in
     * @param selection the packages to build, in the order they are to be built
     * @param diagramIds the diagrams to keep, by {@code xmi:id}; one whose classes did not all make
     *     it is left out whether or not it is named here
     * @param listener receives the progress of the import, answers which diagrams to keep and can
     *     cancel it
     * @return the graphs that were written and, as failed file names, the packages that could not
     *     be built
     */
    ImportResult importPackages(
            String datasetName,
            List<MultipartFile> files,
            List<SelectedPackageDTO> selection,
            List<String> diagramIds,
            ImportProgressListener listener);
}
