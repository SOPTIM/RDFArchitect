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
import org.apache.jena.riot.RDFLanguages;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
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

    private final ChangeLogUseCase changeLogUseCase;
    private final CreateDiagramLayoutUseCase createDiagramLayoutUseCase;
    private final DatabasePort databasePort;

    @Override
    public ImportResult importGraphs(String datasetName, List<MultipartFile> files, List<String> graphUris) {
        var reservedGraphUris = loadExistingGraphUris(datasetName);
        var result = new ImportResult();

        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            if (isZipFile(file)) {
                importZipFile(result, datasetName, file, reservedGraphUris);
            } else {
                var requestedUri = getRequestedGraphUri(graphUris, i);
                importSingleFile(result, datasetName, file, requestedUri, reservedGraphUris);
            }
        }
        return result;
    }

    private String getRequestedGraphUri(List<String> graphUris, int index) {
        if (graphUris != null
                  && graphUris.size() > index
                  && graphUris.get(index) != null
                  && !graphUris.get(index).isBlank()
        ) {
            return graphUris.get(index);
        }
        return null;
    }

    private void importSingleFile(ImportResult result,
                                  String datasetName,
                                  MultipartFile file,
                                  String requestedUri,
                                  Set<String> reservedGraphUris) {
        try {
            var graphUri = normalizeGraphUri(requestedUri, file.getOriginalFilename());
            graphUri = ensureUniqueGraphUri(graphUri, reservedGraphUris);
            var graphIdentifier = replaceGraph(datasetName, graphUri, file);
            result.importedGraphUris().add(graphUri);
            recordChange(graphIdentifier, datasetName);
        } catch (Exception _) {
            result.failedFileNames().add(file.getOriginalFilename());
        }
    }

    private GraphIdentifier replaceGraph(String datasetName, String graphUri, MultipartFile file) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        var graph = parseGraph(file, graphUri);
        databasePort.deleteGraph(graphIdentifier);
        databasePort.createGraph(graphIdentifier, graph);
        return graphIdentifier;
    }

    private void recordChange(GraphIdentifier graphIdentifier, String datasetName) {
        createDiagramLayoutUseCase.createDiagramLayout(graphIdentifier);
        changeLogUseCase.recordChange(
                  graphIdentifier,
                  new ChangeLogEntry(
                            "Imported graph into dataset '" + datasetName + "' with graph URI '"
                                      + graphIdentifier.getGraphUri() + "'.",
                            databasePort.getGraphWithContext(graphIdentifier).getRdfGraph().getLastDelta()
                  )
                                     );
    }

    private void importZipFile(ImportResult result, String datasetName,
                               MultipartFile file, Set<String> reservedGraphUris) {
        try (var zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new DataAccessException("ZIP file contains too many entries.");
                }
                if (entry.getSize() > MAX_ENTRY_SIZE) {
                    throw new DataAccessException("ZIP entry exceeds maximum allowed size: " + entry.getName());
                }
                try {
                    extractAndImportZipEntry(result, datasetName, entry, zipInputStream, reservedGraphUris);
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new DataAccessException("Unable to import graphs from zip file.", exception);
        }
    }

    private void extractAndImportZipEntry(ImportResult result, String datasetName,
                                          ZipEntry entry, ZipInputStream zipInputStream,
                                          Set<String> reservedGraphUris) throws IOException {
        if (entry.isDirectory()) {
            return;
        }
        var entryName = entry.getName();
        if (!isGraphFile(entryName)) {
            logger.warn("Skipping ZIP entry '{}' for dataset '{}' because it is not a supported file.",
                        entryName, datasetName);
            return;
        }
        var extractedFile = toMultipartFile(entryName, zipInputStream);
        importSingleFile(result, datasetName, extractedFile, null, reservedGraphUris);
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

    private MultipartFile toMultipartFile(String fileName, InputStream stream) throws IOException {
        var outputStream = new ByteArrayOutputStream();
        stream.transferTo(outputStream);
        var content = outputStream.toByteArray();
        return new SimpleMultipartFile(fileName, fileName, guessContentType(fileName), content);
    }

    private String buildGraphUriFromFileName(String fileName) {
        var name = Objects.requireNonNullElse(fileName, FALL_BACK_NAME);
        var lastPathSegment = Paths.get(name).getFileName().toString();
        var lastDotIndex = lastPathSegment.lastIndexOf(".");
        var sanitized = lastPathSegment.substring(0, lastDotIndex < 0 ? lastPathSegment.length() : lastDotIndex)
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

    private String guessContentType(String fileName) {
        return Objects.requireNonNullElse(
                  URLConnection.guessContentTypeFromName(fileName),
                  MediaType.APPLICATION_OCTET_STREAM_VALUE
                                         );
    }

    private record SimpleMultipartFile(String name, String originalFilename, String contentType,
                                       byte[] content) implements MultipartFile {

        @Override
        public @NotNull String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte @NotNull [] getBytes() {
            return content;
        }

        @Override
        public @NotNull InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SimpleMultipartFile(String name1, String filename, String type, byte[] content1))) {
                return false;
            }
            return Objects.equals(name, name1)
                      && Objects.equals(originalFilename, filename)
                      && Objects.equals(contentType, type)
                      && Arrays.equals(content, content1);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, originalFilename, contentType);
            result = 31 * result + Arrays.hashCode(content);
            return result;
        }

        @Override
        public @NotNull String toString() {
            return "SimpleMultipartFile[name=%s, originalFilename=%s, contentType=%s, contentLength=%d]"
                      .formatted(name, originalFilename, contentType, content.length);
        }
    }
}
