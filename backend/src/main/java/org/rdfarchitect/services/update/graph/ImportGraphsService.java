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
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.rdfarchitect.services.update.graph.PrefixScanner.ScannedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        databasePort.createWorkspaceIfAbsent(datasetName);
        var sources = planSources(files, graphUris);
        listener.planned(
                sources.stream()
                        .flatMap(source -> source.plannedFiles().stream())
                        .map(PlannedFile::toPlannedImport)
                        .toList());

        var result = new ImportResult();
        var negotiation = negotiatePrefixes(datasetName, sources, listener, result);

        var reservedGraphUris = loadExistingGraphUris(datasetName);
        for (var source : sources) {
            importSource(result, datasetName, source, reservedGraphUris, listener, negotiation);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Namespace prefixes
    // -------------------------------------------------------------------------

    /**
     * Finds the prefixes the import cannot store as they are and has them decided on before the
     * first file is written, which leaves the dataset untouched while the answer is pending. Files
     * the scan cannot read drop out here, before the question is asked rather than after it has
     * been answered.
     */
    private PrefixNegotiation negotiatePrefixes(
            String datasetName,
            List<PlannedSource> sources,
            ImportProgressListener listener,
            ImportResult result) {
        if (listener.isCancelled()) {
            return PrefixNegotiation.none();
        }
        listener.scanningPrefixes();
        var scanned = scan(sources);
        var unreadableIndices = failUnreadable(scanned, result, listener);

        var readableFiles =
                scanned.stream().map(ScanResult::scanned).filter(ScannedFile::readable).toList();
        var comparison = PrefixComparer.compare(loadExistingPrefixes(datasetName), readableFiles);
        var contested = comparison.stream().filter(PrefixComparison::contested).count();
        if (contested == 0) {
            return new PrefixNegotiation(ResolvedPrefixes.none(), unreadableIndices);
        }
        logger.info(
                "Import into dataset \"{}\" contests {} namespace prefix(es).",
                datasetName,
                contested);
        var resolved = listener.awaitResolvedPrefixes(comparison);
        if (!listener.isCancelled()) {
            applyToWorkspacePrefixes(datasetName, resolved);
        }
        return new PrefixNegotiation(resolved, unreadableIndices);
    }

    /** Reports the files the scan could not parse and returns where they sit in the plan. */
    private Set<Integer> failUnreadable(
            List<ScanResult> scanned, ImportResult result, ImportProgressListener listener) {
        var unreadableIndices = new HashSet<Integer>();
        for (var scanResult : scanned) {
            if (scanResult.scanned().readable()) {
                continue;
            }
            var plannedFile = scanResult.plannedFile();
            result.failedFileNames().add(plannedFile.fileName());
            listener.finished(plannedFile.index(), Outcome.FAILED, null);
            unreadableIndices.add(plannedFile.index());
        }
        return unreadableIndices;
    }

    /** Moves the prefixes the dataset holds out of the way, where the decisions call for it. */
    private void applyToWorkspacePrefixes(String datasetName, ResolvedPrefixes resolved) {
        try {
            var current = databasePort.getPrefixMapping(datasetName);
            resolved.rewriteWorkspacePrefixes(current)
                    .ifPresent(rewritten -> databasePort.setPrefixMapping(datasetName, rewritten));
        } catch (RuntimeException exception) {
            logger.warn(
                    "Unable to apply the namespace prefix decisions to dataset \"{}\": {}",
                    datasetName,
                    exception.getMessage());
        }
    }

    /** Reads the prefixes of every planned file, zip entries included. */
    private List<ScanResult> scan(List<PlannedSource> sources) {
        var scanned = new ArrayList<ScanResult>();
        for (var source : sources) {
            if (source.unreadableReason() != null) {
                continue;
            }
            if (!source.zip()) {
                var plannedFile = source.plannedFiles().getFirst();
                scanned.add(
                        new ScanResult(
                                plannedFile,
                                PrefixScanner.scan(plannedFile.fileName(), source.file())));
                continue;
            }
            scanned.addAll(scanZipSource(source));
        }
        return scanned;
    }

    /**
     * The decisions say nothing about the files behind the point the scan stopped at, so those are
     * reported unreadable rather than left for a second pass to bind without anybody being asked.
     */
    private List<ScanResult> scanZipSource(PlannedSource source) {
        var scanned = new ArrayList<ScanResult>();
        var unreached =
                forEachZipEntry(
                        source,
                        "Unable to read all namespace prefixes of zip file '{}': {}",
                        (plannedFile, content) ->
                                scanned.add(
                                        new ScanResult(
                                                plannedFile,
                                                PrefixScanner.scan(
                                                        plannedFile.fileName(),
                                                        InMemoryMultipartFile.of(
                                                                plannedFile.fileName(),
                                                                content)))));
        unreached.forEach(plannedFile -> scanned.add(unscannable(plannedFile)));
        return scanned;
    }

    private ScanResult unscannable(PlannedFile plannedFile) {
        return new ScanResult(
                plannedFile, new ScannedFile(plannedFile.fileName(), Map.of(), false));
    }

    /**
     * Streams the graph files of an archive in the order they were planned in and hands each to
     * {@code process}. A stream that breaks ends the walk as much as one that simply runs out of
     * entries, so both passes over an archive have to know what they never got to see.
     *
     * @param failureMessage logged with the file name and the error when the walk breaks off
     * @return the planned files the walk did not get through, in order
     */
    private List<PlannedFile> forEachZipEntry(
            PlannedSource source, String failureMessage, ZipEntryProcessor process) {
        var plannedFiles = source.plannedFiles().iterator();
        PlannedFile inFlight = null;
        try (var zipInputStream = new ZipInputStream(source.file().getInputStream())) {
            ZipEntry entry;
            while (plannedFiles.hasNext() && (entry = zipInputStream.getNextEntry()) != null) {
                try {
                    if (!isImportableEntry(entry)) {
                        continue;
                    }
                    inFlight = plannedFiles.next();
                    process.accept(inFlight, zipInputStream);
                    inFlight = null;
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        } catch (IOException | RuntimeException exception) {
            logger.warn(
                    failureMessage, source.file().getOriginalFilename(), exception.getMessage());
        }
        var unreached = new ArrayList<PlannedFile>();
        if (inFlight != null) {
            unreached.add(inFlight);
        }
        plannedFiles.forEachRemaining(unreached::add);
        return unreached;
    }

    /** What one pass over an archive does with a single graph file of it. */
    @FunctionalInterface
    private interface ZipEntryProcessor {

        void accept(PlannedFile plannedFile, InputStream content) throws IOException;
    }

    /**
     * The prefixes of the dataset, none when it does not exist yet and so collides with nothing.
     */
    private Map<String, String> loadExistingPrefixes(String datasetName) {
        try {
            return Map.copyOf(databasePort.getPrefixMapping(datasetName).getNsPrefixMap());
        } catch (RuntimeException _) {
            return Map.of();
        }
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
            ImportProgressListener listener,
            PrefixNegotiation negotiation) {
        if (source.unreadableReason() != null) {
            failRemaining(result, source.plannedFiles(), listener, negotiation);
            return;
        }
        if (!source.zip()) {
            var plannedFile = source.plannedFiles().getFirst();
            if (!negotiation.isUnreadable(plannedFile)) {
                importPlannedFile(
                        result,
                        datasetName,
                        plannedFile,
                        source.file(),
                        reservedGraphUris,
                        listener,
                        negotiation.resolved());
            }
            return;
        }

        // The plan was built with the same predicate over the same archive, so streaming it again
        // yields the importable entries in exactly the order they were planned in.
        var unreached =
                forEachZipEntry(
                        source,
                        "Unable to import the remaining graphs of zip file '{}': {}",
                        (plannedFile, content) -> {
                            if (negotiation.isUnreadable(plannedFile)) {
                                return;
                            }
                            if (listener.isCancelled()) {
                                listener.finished(plannedFile.index(), Outcome.SKIPPED, null);
                                return;
                            }
                            importPlannedFile(
                                    result,
                                    datasetName,
                                    plannedFile,
                                    InMemoryMultipartFile.of(plannedFile.fileName(), content),
                                    reservedGraphUris,
                                    listener,
                                    negotiation.resolved());
                        });
        failRemaining(result, unreached, listener, negotiation);
    }

    /**
     * Imports one planned file. Storing the graph is what merges its prefixes into those of the
     * dataset, so the decisions taken on them have to be applied to it first.
     */
    private void importPlannedFile(
            ImportResult result,
            String datasetName,
            PlannedFile plannedFile,
            MultipartFile file,
            Set<String> reservedGraphUris,
            ImportProgressListener listener,
            ResolvedPrefixes resolved) {
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

            var graph = parseGraph(file, graphUri);
            resolved.applyTo(graph.getPrefixMapping());

            var undisplayableProperties = findUndisplayableProperties(graph);
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

    /** Reports the files nothing else will, or the import waits on them forever. */
    private void failRemaining(
            ImportResult result,
            List<PlannedFile> plannedFiles,
            ImportProgressListener listener,
            PrefixNegotiation negotiation) {
        for (var plannedFile : plannedFiles) {
            if (negotiation.isUnreadable(plannedFile)) {
                continue;
            }
            result.failedFileNames().add(plannedFile.fileName());
            listener.finished(plannedFile.index(), Outcome.FAILED, null);
        }
    }

    private void replaceGraph(String datasetName, String graphUri, Graph graph) {
        // The graph is fully built by now, so the write lock is only held for the swap and the
        // workspace stays readable while a large import is being parsed.
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            transaction.deleteGraph(graphUri);
            transaction.createGraph(graphUri, graph);
            transaction.commit("imported graph %s".formatted(graphUri));
        }
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

    private record ScanResult(PlannedFile plannedFile, ScannedFile scanned) {}

    /**
     * What the prefix scan settled: what to do with the prefixes, and which files already failed.
     */
    private record PrefixNegotiation(ResolvedPrefixes resolved, Set<Integer> unreadableIndices) {

        private static PrefixNegotiation none() {
            return new PrefixNegotiation(ResolvedPrefixes.none(), Set.of());
        }

        private boolean isUnreadable(PlannedFile plannedFile) {
            return unreadableIndices.contains(plannedFile.index());
        }
    }
}
