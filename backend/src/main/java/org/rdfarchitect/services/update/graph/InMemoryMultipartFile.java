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
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;

/**
 * A {@link MultipartFile} that holds its content in memory. Used for files extracted from a zip
 * archive, and to take over an upload from the request thread: the servlet container discards the
 * temporary files of a request once its response is sent, so an import that keeps running after
 * that has to own its bytes.
 */
public record InMemoryMultipartFile(
        String name, String originalFilename, String contentType, byte[] content)
        implements MultipartFile {

    /** Reads {@code stream} fully and wraps it as a file named {@code fileName}. */
    public static InMemoryMultipartFile of(String fileName, InputStream stream) throws IOException {
        return new InMemoryMultipartFile(
                fileName, fileName, guessContentType(fileName), stream.readAllBytes());
    }

    /** Copies {@code file}, so that the copy stays readable after the request has ended. */
    public static InMemoryMultipartFile copyOf(MultipartFile file) throws IOException {
        return new InMemoryMultipartFile(
                file.getName(), file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    private static String guessContentType(String fileName) {
        return Objects.requireNonNullElse(
                URLConnection.guessContentTypeFromName(fileName),
                MediaType.APPLICATION_OCTET_STREAM_VALUE);
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
        if (!(o
                instanceof
                InMemoryMultipartFile(
                        String otherName,
                        String otherFilename,
                        String otherType,
                        byte[] otherContent))) {
            return false;
        }
        return Objects.equals(name, otherName)
                && Objects.equals(originalFilename, otherFilename)
                && Objects.equals(contentType, otherType)
                && Arrays.equals(content, otherContent);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, originalFilename, contentType);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "InMemoryMultipartFile[name=%s, originalFilename=%s, contentType=%s, contentLength=%d]"
                .formatted(name, originalFilename, contentType, content.length);
    }
}
