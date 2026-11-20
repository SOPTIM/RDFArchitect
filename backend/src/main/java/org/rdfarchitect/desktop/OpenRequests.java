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

package org.rdfarchitect.desktop;

import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workspace files the operating system asked the desktop app to open, for a double-clicked {@code
 * .rdfa} file. The launcher registers the file and opens a window that names the request; the
 * window then opens the file like one chosen in its Open dialog.
 */
public class OpenRequests {

    /**
     * A file to open.
     *
     * @param id what the window names it by
     * @param path where it is
     */
    public record OpenRequest(String id, Path path) {

        public String fileName() {
            var name = path.getFileName();
            return name == null ? "" : name.toString();
        }
    }

    private final Map<String, OpenRequest> requests = new ConcurrentHashMap<>();

    /**
     * Registers a workspace file to open.
     *
     * @param file the file
     * @return the request naming it
     * @throws IllegalArgumentException if it is not a readable workspace file
     */
    public OpenRequest add(Path file) {
        var path = file.toAbsolutePath().normalize();
        var name = path.getFileName();
        if (name == null
                || !name.toString()
                        .toLowerCase(Locale.ROOT)
                        .endsWith(WorkspaceArchiveFormat.FILE_EXTENSION)) {
            throw new IllegalArgumentException("Not a workspace file: " + file);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Cannot read " + file);
        }
        var request = new OpenRequest(UUID.randomUUID().toString(), path);
        requests.put(request.id(), request);
        return request;
    }

    public Optional<OpenRequest> get(String id) {
        return Optional.ofNullable(id).map(requests::get);
    }

    /** Removes a request once its file is open. */
    public Optional<OpenRequest> remove(String id) {
        return Optional.ofNullable(id).map(requests::remove);
    }
}
