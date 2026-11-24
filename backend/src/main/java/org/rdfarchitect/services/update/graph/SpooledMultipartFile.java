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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MultipartFile} whose content lives in a temporary file this job owns. The servlet
 * container discards the temporary files of a request once its response is sent, so an import that
 * keeps running after that has to take the upload over. It does so on disk rather than on the heap,
 * because a full Enterprise Architect model reaches 100 MB and would otherwise be mirrored in
 * memory for as long as the job lives.
 */
public final class SpooledMultipartFile implements MultipartFile, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(SpooledMultipartFile.class);

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;
    private final long size;

    private SpooledMultipartFile(
            String name, String originalFilename, String contentType, Path path, long size) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.path = path;
        this.size = size;
    }

    /** Copies {@code file} to a temporary file, so that the copy outlives the request. */
    public static SpooledMultipartFile copyOf(MultipartFile file) throws IOException {
        var spooled = Files.createTempFile("rdfa-import-", ".upload");
        try (var content = file.getInputStream()) {
            Files.copy(content, spooled, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(spooled);
            throw exception;
        }
        return new SpooledMultipartFile(
                file.getName(),
                file.getOriginalFilename(),
                Objects.requireNonNullElseGet(
                        file.getContentType(), () -> guessContentType(file.getOriginalFilename())),
                spooled,
                Files.size(spooled));
    }

    /**
     * Deletes the temporary files of every spooled file in {@code files} and ignores the rest. A
     * file that cannot be deleted is logged rather than thrown on: it is the import's outcome that
     * matters to the caller, not the leftover.
     */
    public static void discard(List<MultipartFile> files) {
        for (var file : files) {
            if (!(file instanceof SpooledMultipartFile spooled)) {
                continue;
            }
            try {
                spooled.close();
            } catch (IOException exception) {
                logger.warn(
                        "Unable to delete the temporary file of '{}': {}",
                        spooled.originalFilename,
                        exception.getMessage());
            }
        }
    }

    private static String guessContentType(String fileName) {
        if (fileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return Objects.requireNonNullElse(
                URLConnection.guessContentTypeFromName(fileName),
                MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }

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
        return size == 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    /**
     * Reads the whole file into memory, which is what the {@link MultipartFile} contract asks for.
     * Callers that may see a full model should stream {@link #getInputStream()} instead.
     */
    @Override
    public byte @NotNull [] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(@NotNull File destination) throws IOException {
        Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public @NotNull String toString() {
        return "SpooledMultipartFile[name=%s, originalFilename=%s, contentType=%s, size=%d]"
                .formatted(name, originalFilename, contentType, size);
    }
}
