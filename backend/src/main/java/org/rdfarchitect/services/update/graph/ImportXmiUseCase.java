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
     * @param ownClasses the classes the package declares itself, its sub-packages left out
     * @param ownDependencyClasses what the closure adds on top of those. The two own numbers are
     *     what a container's own classes become when its sub-packages are chosen separately
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
            int ownClasses,
            int ownDependencyClasses) {}

    /**
     * Reads every uploaded export and reports the packages it holds. Nothing is stored: which of
     * them become schemas is a later decision.
     *
     * @param files the uploaded exports
     * @param listener receives the progress of the read and can cancel it
     * @return the packages found, in the order the exports declare them
     */
    List<ScannedPackage> scanPackages(List<MultipartFile> files, ImportProgressListener listener);

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
     * @param listener receives the progress of the import and can cancel it
     * @return the graphs that were written and, as failed file names, the packages that could not
     *     be built
     */
    ImportResult importPackages(
            String datasetName,
            List<MultipartFile> files,
            List<SelectedPackageDTO> selection,
            ImportProgressListener listener);
}
