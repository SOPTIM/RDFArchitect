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
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFLanguages;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.cim.changelog.ChangeLogEntry;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ImportGraphsService implements ImportGraphsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportGraphsService.class);
    private final ChangeLogUseCase changeLogUseCase;

    private final DatabasePort databasePort;
    private static final String FALL_BACK_NAME = "graph";

    @Override
    public List<String> importGraphs(String datasetName, List<MultipartFile> files, List<String> graphUris) {
        var reservedGraphUris = loadExistingGraphUris(datasetName);
        var importedGraphUris = new ArrayList<String>();
        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            if (isZipFile(file)) {
                importedGraphUris.addAll(importZipFile(datasetName, file, reservedGraphUris));
            } else {
                boolean uriProvided = graphUris != null && graphUris.size() > i && graphUris.get(i) != null && !graphUris.get(i).isBlank();
                var requestedGraphUri = uriProvided ? graphUris.get(i) : null;
                var graphUri = normalizeGraphUri(requestedGraphUri, file.getOriginalFilename());
                graphUri = ensureUniqueGraphUri(graphUri, reservedGraphUris);
                var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
                databasePort.deleteGraph(graphIdentifier);
                databasePort.createGraph(graphIdentifier, parseGraph(file, graphUri));
                importedGraphUris.add(graphUri);
            }
        }
        for (var graphUri : importedGraphUris) {
            var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
            changeLogUseCase.recordChange(
                      graphIdentifier,
                      new ChangeLogEntry("Imported graph into dataset '" + datasetName + "' with graph URI '"
                                                   + graphUri + "'.", databasePort.getGraph(graphIdentifier).getLastDelta())
                                         );
        }
        return importedGraphUris;
    }

    private List<String> importZipFile(String datasetName, MultipartFile file, Set<String> reservedGraphUris) {
        try (var zipInputStream = new ZipInputStream(file.getInputStream())) {
            boolean error = false;
            var importedGraphUris = new ArrayList<String>();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }
                var entryName = entry.getName();
                if (!isGraphFile(entryName)) {
                    logger.warn("Skipping ZIP entry '{}' for dataset '{}' because it is not a supported file.", entryName, datasetName);
                    zipInputStream.closeEntry();
                    continue;
                }
                var extractedFile = toMultipartFile(entryName, zipInputStream);
                var graphUri = ensureUniqueGraphUri(buildGraphUriFromFileName(entryName), reservedGraphUris);
                try {
                    var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
                    databasePort.deleteGraph(graphIdentifier);
                    databasePort.createGraph(graphIdentifier, parseGraph(extractedFile, graphUri));
                    importedGraphUris.add(graphUri);
                } catch (RuntimeException exception) {
                    error = true;
                    logger.warn("Skipping ZIP entry '{}' for dataset '{}' because import failed: {}", entryName, datasetName, exception.getMessage(), exception);
                }
                zipInputStream.closeEntry();
            }
            if (error) {
                throw new DataAccessException("One or more graphs could not be imported from the zip file.");
            }
            return importedGraphUris;
        } catch (IOException exception) {
            throw new DataAccessException("Unable to import graphs from zip file.", exception);
        }
    }

    private Set<String> loadExistingGraphUris(String datasetName) {
        try {
            return new HashSet<>(databasePort.listGraphUris(datasetName));
        } catch (RuntimeException exception) {
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
        var originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        return originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private boolean isGraphFile(String fileName) {
        return fileName != null && RDFLanguages.filenameToLang(fileName) != null;
    }

    private String guessContentType(String fileName) {
        return Objects.requireNonNullElse(URLConnection.guessContentTypeFromName(fileName), MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private record SimpleMultipartFile(String name, String originalFilename, String contentType, byte[] content) implements MultipartFile {

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
            if (!(o instanceof SimpleMultipartFile that)) {
                return false;
            }
            return Objects.equals(name, that.name)
                      && Objects.equals(originalFilename, that.originalFilename)
                      && Objects.equals(contentType, that.contentType)
                      && Arrays.equals(content, that.content);
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
