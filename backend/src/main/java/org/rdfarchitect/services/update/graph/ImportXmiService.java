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

import lombok.RequiredArgsConstructor;

import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelWriter;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportResult;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportWarning;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.UncarriedItem;
import org.rdfarchitect.services.update.graph.ImportOutcome.PackagesScanned;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.rdfarchitect.xmi.map.XmiDiagramAvailability;
import org.rdfarchitect.xmi.map.XmiDiagrams;
import org.rdfarchitect.xmi.map.XmiDiagrams.ClassLocation;
import org.rdfarchitect.xmi.map.XmiNamespaces;
import org.rdfarchitect.xmi.map.XmiProfileMapper;
import org.rdfarchitect.xmi.map.XmiProfileMapper.UntypedAttribute;
import org.rdfarchitect.xmi.map.XmiProfileMapper.WrittenProfile;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiPackageTree;
import org.rdfarchitect.xmi.read.XmiPackageTree.XmiPackageNode;
import org.rdfarchitect.xmi.read.XmiReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportXmiService implements ImportXmiUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportXmiService.class);

    private final DatabasePort databasePort;
    private final XmiDiagramStore diagramStore;

    @Override
    public PackagesScanned scanPackages(
            List<MultipartFile> files, ImportProgressListener listener) {
        listener.planned(plan(files));

        var packages = new ArrayList<ScannedPackage>();
        var diagrams = new ArrayList<ScannedDiagram>();
        var supplierGroups = new ArrayList<SupplierGroup>();
        // A pool shared by every package of every export: a package refers to a class by its
        // position here rather than carrying its name, which it would repeat many times over.
        // Keyed by the class rather than by the name, two classes of one name being two classes.
        var classNames = new LinkedHashMap<String, Integer>();
        var pooledNames = new ArrayList<String>();
        var prefixes = new ArrayList<ScannedPrefix>();
        for (var index = 0; index < files.size(); index++) {
            var file = files.get(index);
            if (listener.isCancelled()) {
                listener.finished(index, Outcome.SKIPPED, null);
                continue;
            }
            listener.started(index);
            scanFile(
                    file,
                    index,
                    packages,
                    diagrams,
                    supplierGroups,
                    classNames,
                    pooledNames,
                    prefixes,
                    listener);
        }
        return new PackagesScanned(
                List.copyOf(packages),
                List.copyOf(diagrams),
                List.copyOf(supplierGroups),
                List.copyOf(pooledNames),
                List.copyOf(prefixes));
    }

    /**
     * Reads one export and reports the packages it holds.
     *
     * <p>The whole export is read, not only enough of it to find the profiles: what a package costs
     * to import is the closure over its attribute types and superclasses, and that cannot be worked
     * out from the model section alone. The index is let go of again as soon as the counts are
     * taken — what is kept is one line per package.
     */
    private void scanFile(
            MultipartFile file,
            int index,
            List<ScannedPackage> packages,
            List<ScannedDiagram> diagrams,
            List<SupplierGroup> supplierGroups,
            Map<String, Integer> classNames,
            List<String> pooledNames,
            List<ScannedPrefix> prefixes,
            ImportProgressListener listener) {
        var fileName = file.getOriginalFilename();
        if (!XmiFiles.isXmiFile(file)) {
            logger.warn("'{}' is not an Enterprise Architect export.", fileName);
            listener.finished(index, Outcome.FAILED, null);
            return;
        }
        try (var xmi =
                ReportingInputStream.of(
                        file.getInputStream(),
                        file.getSize(),
                        done -> listener.progressed(index, done, file.getSize()))) {
            var read = XmiReader.read(xmi);
            var found = XmiPackageTree.of(read);
            var modelNamespace = XmiNamespaces.modelNamespaceOf(read);
            var extensionNamespace = XmiNamespaces.extensionNamespaceOf(read);
            var hasExtensions = XmiNamespaces.hasExtensionElements(read);
            read.getPrefixes()
                    .forEach(
                            (prefix, namespace) ->
                                    prefixes.add(new ScannedPrefix(index, prefix, namespace)));
            var availability = XmiDiagramAvailability.of(read);
            availability
                    .diagrams()
                    .forEach(
                            drawn ->
                                    diagrams.add(
                                            new ScannedDiagram(
                                                    index,
                                                    drawn.id(),
                                                    drawn.packageId(),
                                                    drawn.name(),
                                                    drawn.classes(),
                                                    drawn.supplierGroups())));
            for (var group = 0; group < availability.supplierGroups().size(); group++) {
                var suppliers = availability.supplierGroups().get(group);
                supplierGroups.add(
                        new SupplierGroup(
                                index, group, suppliers.packageIds(), suppliers.packageOnlyIds()));
            }
            for (var node : found) {
                packages.add(
                        new ScannedPackage(
                                index,
                                fileName,
                                node.id(),
                                node.parentId(),
                                node.name(),
                                node.profile(),
                                node.namespaceCandidates(),
                                node.declaredClasses(),
                                node.dependencyClasses(),
                                pooled(node.ownClassIds(), read, classNames, pooledNames),
                                pooled(node.ownDependencyClassIds(), read, classNames, pooledNames),
                                modelNamespace,
                                extensionNamespace,
                                hasExtensions));
            }
            logger.info(
                    "Export '{}' holds {} package(s), {} of them a profile.",
                    fileName,
                    found.size(),
                    found.stream().filter(XmiPackageNode::profile).count());
            listener.finished(index, Outcome.SCANNED, null);
        } catch (IOException | RuntimeException exception) {
            logger.warn("Unable to read the export '{}': {}", fileName, exception.getMessage());
            listener.finished(index, Outcome.FAILED, null);
        }
    }

    /** The classes as positions in the pool, adding the ones the pool does not hold yet. */
    private List<Integer> pooled(
            List<String> classIds, XmiIndex index, Map<String, Integer> pool, List<String> names) {
        return classIds.stream()
                .map(
                        classId ->
                                pool.computeIfAbsent(
                                        classId,
                                        _ -> {
                                            names.add(index.getClasses().get(classId).getName());
                                            return names.size() - 1;
                                        }))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Importing the chosen packages
    // -------------------------------------------------------------------------

    /**
     * Works through the selection one export at a time. The progress is reported per chosen package
     * rather than per file, because a package is what becomes a schema and what can fail on its
     * own; which export it came out of is of no further interest once it has been read.
     *
     * <p>Cancelling takes the schemas back out again. An import of rdf files keeps what it wrote,
     * because each file is a thing the caller asked for on its own; this one is a single act
     * carried out in steps, and stopping it halfway would leave a workspace that was never asked
     * for.
     */
    @Override
    public ImportResult importPackages(
            String datasetName,
            List<MultipartFile> files,
            List<SelectedPackageDTO> selection,
            List<String> diagramIds,
            ImportProgressListener listener) {
        var chosenDiagrams = Set.copyOf(diagramIds);
        var work = plan(selection, files);
        listener.planned(work.stream().flatMap(export -> export.plannedSteps().stream()).toList());

        var result = new ImportResult();
        var workspaceExisted = workspaceExists(datasetName);
        var reservedGraphUris = loadExistingGraphUris(datasetName);
        for (var export : work) {
            importFrom(datasetName, export, reservedGraphUris, chosenDiagrams, listener, result);
        }
        if (listener.isCancelled()) {
            rollBack(datasetName, result, workspaceExisted);
        }
        return result;
    }

    /**
     * Undoes what the import wrote. Only its own graphs, and the workspace only where the import
     * brought it into being: one that was already there holds schemas this import has no say over.
     */
    private void rollBack(String datasetName, ImportResult result, boolean workspaceExisted) {
        for (var graphUri : result.importedGraphUris()) {
            try {
                databasePort.deleteGraph(new GraphIdentifier(datasetName, graphUri));
            } catch (RuntimeException exception) {
                logger.warn(
                        "Unable to take back the schema \"{}\": {}",
                        graphUri,
                        exception.getMessage());
            }
        }
        logger.info("Import cancelled: took back {} schema(s).", result.importedGraphUris().size());
        result.importedGraphUris().clear();
        if (!workspaceExisted) {
            try {
                databasePort.deleteDataset(datasetName);
            } catch (RuntimeException exception) {
                logger.warn(
                        "Unable to take back the workspace \"{}\": {}",
                        datasetName,
                        exception.getMessage());
            }
        }
    }

    private boolean workspaceExists(String datasetName) {
        try {
            return databasePort.listDatasets().contains(datasetName);
        } catch (RuntimeException _) {
            return false;
        }
    }

    /**
     * Reads one export and writes the packages chosen from it, then its diagrams. The export is
     * read once however many packages were chosen from it, and let go of again before the next one
     * is opened: a full model is 100 MB of file and rather more as an index.
     */
    private void importFrom(
            String datasetName,
            ExportWork export,
            Set<String> reservedGraphUris,
            Set<String> chosenDiagrams,
            ImportProgressListener listener,
            ImportResult result) {
        XmiIndex index;
        // Reported against the first package of the export: reading it is what that step waits
        // for, and it is the one part of an import whose length is known in advance.
        var readStep = export.packages().isEmpty() ? -1 : export.packages().getFirst().index();
        try (var xmi =
                ReportingInputStream.of(
                        export.file().getInputStream(),
                        export.file().getSize(),
                        done -> listener.progressed(readStep, done, export.file().getSize()))) {
            index = XmiReader.read(xmi);
        } catch (IOException | RuntimeException exception) {
            logger.warn(
                    "Unable to read the export '{}': {}",
                    export.file().getOriginalFilename(),
                    exception.getMessage());
            failAll(export.packages(), listener, result);
            listener.finished(export.diagramIndex(), Outcome.SKIPPED, null);
            return;
        }

        var locations = new HashMap<String, ClassLocation>();
        for (var plannedProfile : export.packages()) {
            writePackage(
                    datasetName,
                    index,
                    plannedProfile,
                    reservedGraphUris,
                    listener,
                    result,
                    locations);
        }
        storeDiagrams(datasetName, export, index, locations, chosenDiagrams, listener);
    }

    private void writePackage(
            String datasetName,
            XmiIndex index,
            PlannedProfile plannedProfile,
            Set<String> reservedGraphUris,
            ImportProgressListener listener,
            ImportResult result,
            Map<String, ClassLocation> locations) {
        if (listener.isCancelled()) {
            listener.finished(plannedProfile.index(), Outcome.SKIPPED, null);
            return;
        }
        listener.started(plannedProfile.index());
        try {
            var profile = index.schemaOf(plannedProfile.selected().packageId());
            var graphUri =
                    GraphUris.ensureUnique(
                            GraphUris.normalize(
                                    plannedProfile.selected().graphUri(), profile.name()),
                            reservedGraphUris);

            var model = ModelFactory.createDefaultModel();
            var writer = new CIMModelWriter(graphUri, model);
            var written =
                    XmiProfileMapper.write(
                            index,
                            profile,
                            plannedProfile.selected().scopeOrDefault(),
                            writer,
                            new XmiNamespaces.Supplied(
                                    plannedProfile.selected().modelNamespace(),
                                    plannedProfile.selected().extensionNamespace()));
            written.classes()
                    .forEach(
                            (classId, writtenClass) ->
                                    locations.put(
                                            classId,
                                            new ClassLocation(
                                                    writtenClass.uuid(),
                                                    writtenClass.uri(),
                                                    graphUri)));
            warnAboutUntypedAttributes(plannedProfile, graphUri, written, result);
            warnAboutDuplicateClasses(plannedProfile, graphUri, written, result);
            warnAboutUnmappedOntologyFields(plannedProfile, graphUri, written, result);

            replaceGraph(datasetName, graphUri, writer);
            result.importedGraphUris().add(graphUri);
            listener.finished(plannedProfile.index(), Outcome.IMPORTED, graphUri);
            logger.info(
                    "Imported profile '{}' as \"{}\" with {} class(es).",
                    profile.name(),
                    graphUri,
                    written.classes().size());
        } catch (RuntimeException exception) {
            logger.warn(
                    "Unable to import the profile '{}': {}",
                    plannedProfile.profileName(),
                    exception.getMessage());
            result.failedFileNames().add(plannedProfile.profileName());
            listener.finished(plannedProfile.index(), Outcome.FAILED, null);
        }
    }

    /**
     * Reports the attributes that were written without a datatype, naming what the export points at
     * where it says so at all.
     *
     * <p>This is what a partial export costs. Enterprise Architect writes an {@code EAStub} for
     * every element it leaves out, which is enough to name the missing type and not enough to write
     * it, so the attribute goes in untyped and the caller is told which package to export alongside
     * next time.
     */
    private void warnAboutUntypedAttributes(
            PlannedProfile plannedProfile,
            String graphUri,
            WrittenProfile written,
            ImportResult result) {
        if (written.untypedAttributes().isEmpty()) {
            return;
        }
        var missingTypes =
                written.untypedAttributes().stream()
                        .map(UntypedAttribute::missingType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
        var described =
                written.untypedAttributes().stream().map(ImportXmiService::uncarried).toList();
        result.warnings().add(new ImportWarning(plannedProfile.profileName(), graphUri, described));
        logger.warn(
                "Profile '{}': {} attribute(s) written without a datatype. The export refers to {}"
                        + " but does not contain {}.",
                plannedProfile.profileName(),
                written.untypedAttributes().size(),
                missingTypes.isEmpty() ? "types" : missingTypes,
                missingTypes.isEmpty() ? "them" : "it");
    }

    /**
     * Reports the classes that were left out for sharing a uri with one already written.
     *
     * <p>Not a failure of the import: the resource is there, under the first of the two
     * declarations. What is lost is whatever the second said differently, which is why it is named
     * rather than passed over.
     */
    private void warnAboutDuplicateClasses(
            PlannedProfile plannedProfile,
            String graphUri,
            WrittenProfile written,
            ImportResult result) {
        if (written.duplicateClasses().isEmpty()) {
            return;
        }
        var described =
                written.duplicateClasses().stream()
                        .map(
                                duplicate ->
                                        new UncarriedItem(
                                                duplicate.name(),
                                                "declared more than once; the first is kept"))
                        .toList();
        result.warnings().add(new ImportWarning(plannedProfile.profileName(), graphUri, described));
        logger.warn(
                "Profile '{}': {} class(es) the export declares more than once; the first"
                        + " declaration of each is kept.",
                plannedProfile.profileName(),
                written.duplicateClasses().size());
    }

    /**
     * Reports the ontology header fields the export states under a name RDFArchitect has no
     * predicate for, so they are not written.
     */
    private void warnAboutUnmappedOntologyFields(
            PlannedProfile plannedProfile,
            String graphUri,
            WrittenProfile written,
            ImportResult result) {
        if (written.unmappedOntologyFields().isEmpty()) {
            return;
        }
        var described =
                written.unmappedOntologyFields().stream()
                        .map(
                                field ->
                                        new UncarriedItem(
                                                field.name(), "no ontology field of that name"))
                        .toList();
        result.warnings().add(new ImportWarning(plannedProfile.profileName(), graphUri, described));
    }

    /** An untyped attribute as the dialog shows it: the attribute and what it wanted to be. */
    private static UncarriedItem uncarried(UntypedAttribute untyped) {
        var name = untyped.attributeUri();
        var local = name.substring(name.indexOf('#') + 1);
        return new UncarriedItem(
                local,
                untyped.missingType() == null
                        ? "the export states no type that is in the file"
                        : untyped.missingType() + " is not in the export");
    }

    /**
     * Stores the diagrams, reported as a step of its own.
     *
     * <p>It is part of the plan rather than something that happens after it, because it takes
     * longer than writing the schemas on a large one and a progress that was already complete would
     * leave the caller watching a finished bar.
     */
    private void storeDiagrams(
            String datasetName,
            ExportWork export,
            XmiIndex index,
            Map<String, ClassLocation> locations,
            Set<String> chosenDiagrams,
            ImportProgressListener listener) {
        if (locations.isEmpty() || listener.isCancelled()) {
            listener.finished(export.diagramIndex(), Outcome.SKIPPED, null);
            return;
        }
        listener.started(export.diagramIndex());
        var offered = XmiDiagrams.of(index, locations);
        var stored =
                diagramStore.store(
                        datasetName,
                        offered.stream()
                                .filter(diagram -> chosenDiagrams.contains(diagram.exportId()))
                                .toList(),
                        listener::isCancelled);
        listener.finished(export.diagramIndex(), Outcome.IMPORTED, null);
        logger.info(
                "Stored {} of the {} diagram(s) offered, out of the export's {}; the rest draw"
                        + " classes that were not imported.",
                stored,
                offered.size(),
                index.getDiagrams().size());
    }

    private void replaceGraph(String datasetName, String graphUri, CIMModelWriter writer) {
        // Created at the first write rather than up front, the way importing rdf files does it: an
        // import that writes nothing leaves no workspace behind.
        databasePort.createWorkspaceIfAbsent(datasetName);

        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        databasePort.deleteGraph(graphIdentifier);
        databasePort.createGraph(graphIdentifier, writer.graph());
    }

    /** Reports the packages of an export nothing else will, or the import waits on them forever. */
    private void failAll(
            List<PlannedProfile> chosen, ImportProgressListener listener, ImportResult result) {
        for (var plannedProfile : chosen) {
            result.failedFileNames().add(plannedProfile.profileName());
            listener.finished(plannedProfile.index(), Outcome.FAILED, null);
        }
    }

    private Set<String> loadExistingGraphUris(String datasetName) {
        try {
            return new HashSet<>(databasePort.listGraphUris(datasetName));
        } catch (RuntimeException _) {
            return new HashSet<>();
        }
    }

    // -------------------------------------------------------------------------
    // Planning
    // -------------------------------------------------------------------------

    private List<PlannedImport> plan(List<MultipartFile> files) {
        var planned = new ArrayList<PlannedImport>(files.size());
        for (var index = 0; index < files.size(); index++) {
            var file = files.get(index);
            planned.add(new PlannedImport(index, file.getOriginalFilename(), file.getSize()));
        }
        return planned;
    }

    /**
     * The work ahead, one entry per export the selection names, each with the packages chosen from
     * it and the step its diagrams are.
     *
     * <p>A selection naming an export that was never read is left out rather than planned and
     * failed: nothing can be said about it, not even a name.
     */
    private List<ExportWork> plan(List<SelectedPackageDTO> selection, List<MultipartFile> files) {
        var byFile = new LinkedHashMap<MultipartFile, List<SelectedPackageDTO>>();
        for (var selected : selection) {
            if (selected.fileIndex() < 0 || selected.fileIndex() >= files.size()) {
                logger.warn(
                        "Ignoring the choice of package {}: it names an export that was not read.",
                        selected.packageId());
                continue;
            }
            byFile.computeIfAbsent(files.get(selected.fileIndex()), _ -> new ArrayList<>())
                    .add(selected);
        }

        // The packages first and the diagram steps after them, so that the progress reads as the
        // order the work is done in per export.
        var steps = 0;
        var work = new ArrayList<ExportWork>();
        for (var entry : byFile.entrySet()) {
            var packages = new ArrayList<PlannedProfile>();
            for (var selected : entry.getValue()) {
                packages.add(new PlannedProfile(steps++, entry.getKey(), selected));
            }
            work.add(new ExportWork(entry.getKey(), packages, steps++));
        }
        return work;
    }

    /**
     * One export of the import: the packages chosen from it and the step its diagrams are.
     *
     * @param diagramIndex position of the diagram step in the progress
     */
    private record ExportWork(MultipartFile file, List<PlannedProfile> packages, int diagramIndex) {

        private List<PlannedImport> plannedSteps() {
            var steps = new ArrayList<PlannedImport>();
            packages.forEach(plannedPackage -> steps.add(plannedPackage.toPlannedImport()));
            steps.add(new PlannedImport(diagramIndex, "Diagrams", -1));
            return steps;
        }
    }

    /**
     * One package of the import, in the order the import will build it.
     *
     * @param index position in the progress, which is per package and not per file
     */
    private record PlannedProfile(int index, MultipartFile file, SelectedPackageDTO selected) {

        private String profileName() {
            return selected.name() == null || selected.name().isBlank()
                    ? selected.packageId()
                    : selected.name();
        }

        private PlannedImport toPlannedImport() {
            // No size: what a package costs to build has nothing to do with the bytes of the
            // export it came out of, and the two would be summed into one progress bar.
            return new PlannedImport(index, profileName(), -1);
        }
    }
}
