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

import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ImportGraphsService implements ImportGraphsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportGraphsService.class);

    private static final long MAX_ENTRY_SIZE = FileUtils.ONE_GB;
    private static final int MAX_ENTRIES = 1000;
    private static final String FALL_BACK_NAME = "graph";

    private final DatabasePort databasePort;

    @Override
    public ImportResult importGraphs(
            String datasetName,
            List<MultipartFile> files,
            List<String> graphUris,
            ImportProgressListener listener) {
        var sources = planSources(files, graphUris);
        listener.planned(
                sources.stream()
                        .flatMap(source -> source.plannedFiles().stream())
                        .map(PlannedFile::toPlannedImport)
                        .toList());

        var reservedGraphUris = loadExistingGraphUris(datasetName);
        var result = new ImportResult();
        for (var source : sources) {
            importSource(result, datasetName, source, reservedGraphUris, listener);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Planning
    // -------------------------------------------------------------------------

    /**
     * Works out which graph files the upload will produce, before importing any of them, so that
     * progress can be reported against a known total. A zip archive contributes one entry per graph
     * file it holds; an archive that cannot be read contributes a single entry that fails right
     * away, leaving the other uploads unaffected.
     */
    private List<PlannedSource> planSources(List<MultipartFile> files, List<String> graphUris) {
        var sources = new ArrayList<PlannedSource>();
        int index = 0;
        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            if (isZipFile(file)) {
                var source = planZipSource(file, index);
                index += source.plannedFiles().size();
                sources.add(source);
            } else {
                var fileName =
                        Objects.requireNonNullElse(file.getOriginalFilename(), FALL_BACK_NAME);
                var plannedFile =
                        new PlannedFile(
                                index++,
                                fileName,
                                getRequestedGraphUri(graphUris, i),
                                file.getSize());
                sources.add(new PlannedSource(file, false, List.of(plannedFile), null));
            }
        }
        return sources;
    }

    private PlannedSource planZipSource(MultipartFile file, int firstIndex) {
        var plannedFiles = new ArrayList<PlannedFile>();
        var index = firstIndex;
        try (var zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Zip file contains too many entries.");
                }
                if (entry.getSize() > MAX_ENTRY_SIZE) {
                    throw new IOException(
                            "Zip entry exceeds maximum allowed size: " + entry.getName());
                }
                if (isImportableEntry(entry)) {
                    plannedFiles.add(
                            new PlannedFile(index++, entry.getName(), null, entry.getSize()));
                } else if (!entry.isDirectory()) {
                    logger.warn(
                            "Skipping zip entry '{}' because it is not a supported file.",
                            entry.getName());
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException | RuntimeException exception) {
            logger.warn(
                    "Unable to read zip file '{}': {}",
                    file.getOriginalFilename(),
                    exception.getMessage());
            return unreadableZipSource(file, firstIndex, exception.getMessage());
        }
        if (plannedFiles.isEmpty()) {
            // Reported as a failure of the archive itself: a zip that contributes nothing would
            // otherwise disappear from the progress without any hint of why.
            return unreadableZipSource(file, firstIndex, "Contains no supported graph file.");
        }
        return new PlannedSource(file, true, plannedFiles, null);
    }

    private PlannedSource unreadableZipSource(MultipartFile file, int index, String reason) {
        var fileName = Objects.requireNonNullElse(file.getOriginalFilename(), FALL_BACK_NAME);
        return new PlannedSource(
                file,
                true,
                List.of(new PlannedFile(index, fileName, null, file.getSize())),
                reason);
    }

    private boolean isImportableEntry(ZipEntry entry) {
        return !entry.isDirectory() && isGraphFile(entry.getName());
    }

    private String getRequestedGraphUri(List<String> graphUris, int index) {
        if (graphUris != null
                && graphUris.size() > index
                && graphUris.get(index) != null
                && !graphUris.get(index).isBlank()) {
            return graphUris.get(index);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Importing
    // -------------------------------------------------------------------------

    private void importSource(
            ImportResult result,
            String datasetName,
            PlannedSource source,
            Set<String> reservedGraphUris,
            ImportProgressListener listener) {
        var plannedFiles = source.plannedFiles().iterator();

        if (source.unreadableReason() != null) {
            failRemaining(result, plannedFiles, listener);
            return;
        }
        if (!source.zip()) {
            importPlannedFile(
                    result,
                    datasetName,
                    plannedFiles.next(),
                    source.file(),
                    reservedGraphUris,
                    listener);
            return;
        }

        // The plan was built with the same predicate over the same archive, so streaming it again
        // yields the importable entries in exactly the order they were planned in.
        try (var zipInputStream = new ZipInputStream(source.file().getInputStream())) {
            ZipEntry entry;
            while (plannedFiles.hasNext() && (entry = zipInputStream.getNextEntry()) != null) {
                try {
                    if (!isImportableEntry(entry)) {
                        continue;
                    }
                    var plannedFile = plannedFiles.next();
                    if (listener.isCancelled()) {
                        listener.finished(plannedFile.index(), Outcome.SKIPPED, null);
                        continue;
                    }
                    importPlannedFile(
                            result,
                            datasetName,
                            plannedFile,
                            InMemoryMultipartFile.of(plannedFile.fileName(), zipInputStream),
                            reservedGraphUris,
                            listener);
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        } catch (IOException exception) {
            logger.warn(
                    "Unable to import the remaining graphs of zip file '{}': {}",
                    source.file().getOriginalFilename(),
                    exception.getMessage());
            failRemaining(result, plannedFiles, listener);
        }
    }

    private void importPlannedFile(
            ImportResult result,
            String datasetName,
            PlannedFile plannedFile,
            MultipartFile file,
            Set<String> reservedGraphUris,
            ImportProgressListener listener) {
        if (listener.isCancelled()) {
            listener.finished(plannedFile.index(), Outcome.SKIPPED, null);
            return;
        }
        listener.started(plannedFile.index());
        try {
            var graphUri =
                    ensureUniqueGraphUri(
                            normalizeGraphUri(
                                    plannedFile.requestedGraphUri(), plannedFile.fileName()),
                            reservedGraphUris);

            listener.stage(plannedFile.index(), Stage.PARSING);
            var graph = parseGraph(file, graphUri);

            listener.stage(plannedFile.index(), Stage.ANALYZING);
            var undisplayableProperties = findUndisplayableProperties(graph);

            listener.stage(plannedFile.index(), Stage.STORING);
            replaceGraph(datasetName, graphUri, graph);

            result.importedGraphUris().add(graphUri);
            if (!undisplayableProperties.isEmpty()) {
                result.warnings()
                        .add(new ImportWarning(plannedFile.fileName(), undisplayableProperties));
            }
            listener.finished(plannedFile.index(), Outcome.IMPORTED, graphUri);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Unable to import '{}': {}", plannedFile.fileName(), exception.getMessage());
            result.failedFileNames().add(plannedFile.fileName());
            listener.finished(plannedFile.index(), Outcome.FAILED, null);
        }
    }

    private void failRemaining(
            ImportResult result,
            Iterator<PlannedFile> plannedFiles,
            ImportProgressListener listener) {
        while (plannedFiles.hasNext()) {
            var plannedFile = plannedFiles.next();
            result.failedFileNames().add(plannedFile.fileName());
            listener.finished(plannedFile.index(), Outcome.FAILED, null);
        }
    }

    private void replaceGraph(String datasetName, String graphUri, Graph graph) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        databasePort.deleteGraph(graphIdentifier);
        databasePort.createGraph(graphIdentifier, graph);
    }

    /**
     * Finds properties that are imported but will not be displayed in the editor. RDFArchitect only
     * renders an {@code rdf:Property} that has a domain as an attribute (when it carries the {@code
     * UML#attribute} stereotype) or as an association (when it carries {@code
     * cims:AssociationUsed}). A domain-bound property with neither marker is stored but stays
     * invisible, so we surface it as a warning instead of dropping it silently.
     *
     * @param graph the parsed graph to inspect
     * @return the names (labels, falling back to URIs) of properties that will not be displayed
     */
    private List<String> findUndisplayableProperties(Graph graph) {
        var model = ModelFactory.createModelForGraph(graph);
        var undisplayableProperties = new ArrayList<String>();
        model.listSubjectsWithProperty(RDF.type, RDF.Property)
                .filterKeep(Resource::isURIResource)
                .filterKeep(property -> property.hasProperty(RDFS.domain))
                .filterDrop(
                        property -> property.hasProperty(CIMS.stereotype, CIMStereotypes.attribute))
                .filterDrop(property -> property.hasProperty(CIMS.associationUsed))
                .forEachRemaining(property -> undisplayableProperties.add(propertyName(property)));
        return undisplayableProperties;
    }

    private String propertyName(Resource property) {
        var label = property.getProperty(RDFS.label);
        if (label != null && label.getObject().isLiteral()) {
            return label.getString();
        }
        var localName = property.getLocalName();
        return localName == null || localName.isBlank() ? property.getURI() : localName;
    }

    private Set<String> loadExistingGraphUris(String datasetName) {
        try {
            return new HashSet<>(databasePort.listGraphUris(datasetName));
        } catch (RuntimeException _) {
            return new HashSet<>();
        }
    }

    private Graph parseGraph(MultipartFile file, String graphUri) {
        return new GraphFileSourceBuilderImpl()
                .setFile(file)
                .setGraphName(graphUri)
                .build()
                .graph();
    }

    private String ensureUniqueGraphUri(String graphUri, Set<String> reservedGraphUris) {
        var candidate = graphUri;
        int suffix = 1;
        while (reservedGraphUris.contains(candidate)) {
            candidate = graphUri + "_" + suffix++;
        }
        reservedGraphUris.add(candidate);
        return candidate;
    }

    private String buildGraphUriFromFileName(String fileName) {
        var name = Objects.requireNonNullElse(fileName, FALL_BACK_NAME);
        var fileNamePath = Paths.get(name).getFileName();
        var lastPathSegment = fileNamePath == null ? FALL_BACK_NAME : fileNamePath.toString();
        var lastDotIndex = lastPathSegment.lastIndexOf(".");
        var sanitized =
                lastPathSegment
                        .substring(0, lastDotIndex < 0 ? lastPathSegment.length() : lastDotIndex)
                        .replaceAll("\\W", "_");

        if (sanitized.isBlank()) {
            sanitized = FALL_BACK_NAME;
        }
        return RDFA.GRAPH_URI + sanitized;
    }

    private String normalizeGraphUri(String requestedUri, String fallbackFileName) {
        if (requestedUri == null || requestedUri.isBlank()) {
            return buildGraphUriFromFileName(fallbackFileName);
        }
        var trimmed = requestedUri.trim();
        if (trimmed.contains("://")) {
            return trimmed;
        }
        return RDFA.GRAPH_URI + trimmed;
    }

    private boolean isZipFile(MultipartFile file) {
        var originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        return originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private boolean isGraphFile(String fileName) {
        return fileName != null && RDFLanguages.filenameToLang(fileName) != null;
    }

    /**
     * One uploaded file together with the graph files it contributes to the import.
     *
     * @param unreadableReason why the archive could not be opened, or {@code null} when it could
     */
    private record PlannedSource(
            MultipartFile file,
            boolean zip,
            List<PlannedFile> plannedFiles,
            String unreadableReason) {}

    /** One graph file of the import, in the order the import will process it. */
    private record PlannedFile(
            int index, String fileName, String requestedGraphUri, long sizeBytes) {

        PlannedImport toPlannedImport() {
            return new PlannedImport(index, fileName, sizeBytes);
        }
    }
}
