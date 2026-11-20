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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

/**
 * Keeps a copy of every unsaved workspace on disk, so that work survives the desktop app stopping
 * without a save: a window closed despite the warning, a crashed browser, a computer shutting down.
 *
 * <p>While the app runs, {@link #writeUnsaved()} writes each workspace with changes no file has as
 * a workspace file into {@code recovery/} of the app's data directory, and removes the copy again
 * once the workspace is saved or deleted. It runs periodically and once more when the app stops. At
 * the next start the copies left behind are moved to {@code recovery/previous-run/}, where the user
 * interface offers them for restoring.
 *
 * <p>Workspaces live in sessions, and nothing here has a request to name one, so the sessions are
 * those that windows sent a heartbeat from ({@link #noteSession()}).
 *
 * <p>Without a directory (no {@code desktop.data-directory}) nothing is kept.
 */
public class WorkspaceRecovery {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceRecovery.class);

    private static final String PREVIOUS_RUN = "previous-run";
    private static final Pattern FILE_NAME = Pattern.compile("[^/\\\\:]+\\.rdfa");

    /**
     * A workspace kept from the previous run.
     *
     * @param id the file it is kept in, to address it by
     * @param workspaceName the name the workspace had
     * @param savedAt when the copy was written
     */
    public record RecoveredWorkspace(String id, String workspaceName, Instant savedAt) {}

    private record Key(String sessionId, String workspaceName) {}

    private record Copy(Path file, UUID version) {}

    private final Path directory;
    private final Path previousRun;
    private final DatabasePort databasePort;
    private final SavedWorkspaces savedWorkspaces;
    private final ExportWorkspaceUseCase exportWorkspaceUseCase;

    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final Map<Key, Copy> copies = new HashMap<>();

    /**
     * @param directory the {@code recovery} directory of the app's data directory, or {@code null}
     *     to keep nothing
     */
    public WorkspaceRecovery(
            Path directory,
            DatabasePort databasePort,
            SavedWorkspaces savedWorkspaces,
            ExportWorkspaceUseCase exportWorkspaceUseCase) {
        this.directory = directory;
        this.previousRun = directory != null ? directory.resolve(PREVIOUS_RUN) : null;
        this.databasePort = databasePort;
        this.savedWorkspaces = savedWorkspaces;
        this.exportWorkspaceUseCase = exportWorkspaceUseCase;
        if (directory != null) {
            setAsidePreviousRun();
        }
    }

    /** Keeps what is unsaved one last time; called when the app stops. */
    public void close() {
        writeUnsaved();
    }

    /** Records that the current session has a window, so its workspaces are kept as well. */
    public void noteSession() {
        sessions.add(SessionContext.getSessionId());
    }

    /**
     * Writes a copy of every unsaved workspace whose version has none yet, and removes the copies
     * of workspaces that are saved or gone.
     */
    public synchronized void writeUnsaved() {
        if (directory == null) {
            return;
        }
        for (var sessionId : List.copyOf(sessions)) {
            SessionContext.setSessionId(sessionId);
            try {
                writeUnsavedOfCurrentSession(sessionId);
            } catch (RuntimeException e) {
                logger.error("Could not keep the unsaved workspaces of a session.", e);
            } finally {
                SessionContext.clear();
            }
        }
    }

    private void writeUnsavedOfCurrentSession(String sessionId) {
        var unsaved = savedWorkspaces.unsavedWorkspaces();
        for (var workspaceName : unsaved) {
            var key = new Key(sessionId, workspaceName);
            var version = databasePort.currentChangeId(workspaceName);
            var copy = copies.get(key);
            if (version == null || (copy != null && version.equals(copy.version()))) {
                // Being written to right now, or kept as it stands already.
                continue;
            }
            try {
                var file = copy != null ? copy.file() : fileFor(key);
                writeAtomically(file, exportWorkspaceUseCase.exportWorkspace(workspaceName));
                copies.put(key, new Copy(file, version));
                logger.info("Kept a copy of unsaved workspace \"{}\".", workspaceName);
            } catch (IOException | ResourceNotFoundException e) {
                logger.warn("Could not keep a copy of workspace \"{}\".", workspaceName, e);
            }
        }
        var obsolete =
                copies.keySet().stream()
                        .filter(key -> key.sessionId().equals(sessionId))
                        .filter(key -> !unsaved.contains(key.workspaceName()))
                        .toList();
        for (var key : obsolete) {
            deleteQuietly(copies.remove(key).file());
        }
    }

    /** Returns the workspaces kept from the previous run, newest first. */
    public List<RecoveredWorkspace> previousRun() {
        if (previousRun == null || !Files.isDirectory(previousRun)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(previousRun)) {
            return files.filter(file -> FILE_NAME.matcher(nameOf(file)).matches())
                    .map(this::describe)
                    .sorted((a, b) -> b.savedAt().compareTo(a.savedAt()))
                    .toList();
        } catch (IOException e) {
            logger.warn("Could not list the workspaces kept from the previous run.", e);
            return List.of();
        }
    }

    /** Returns the workspace file of a workspace kept from the previous run. */
    public Optional<byte[]> read(String id) {
        var file = previousRunFile(id);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file.get()));
        } catch (IOException e) {
            logger.warn("Could not read the kept workspace {}.", id, e);
            return Optional.empty();
        }
    }

    /**
     * Removes a workspace kept from the previous run, once it is restored or not wanted.
     *
     * @return whether there was one
     */
    public boolean discard(String id) {
        var file = previousRunFile(id);
        file.ifPresent(WorkspaceRecovery::deleteQuietly);
        return file.isPresent();
    }

    private Optional<Path> previousRunFile(String id) {
        if (previousRun == null || id == null || !FILE_NAME.matcher(id).matches()) {
            return Optional.empty();
        }
        var file = previousRun.resolve(id).normalize();
        var parent = file.getParent();
        if (parent == null
                || !parent.equals(previousRun.normalize())
                || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private RecoveredWorkspace describe(Path file) {
        var id = nameOf(file);
        Instant savedAt;
        try {
            savedAt = Files.getLastModifiedTime(file).toInstant();
        } catch (IOException _) {
            savedAt = Instant.EPOCH;
        }
        return new RecoveredWorkspace(id, workspaceNameIn(file).orElse(nameFromFile(id)), savedAt);
    }

    /** The name in the file's manifest, the name the workspace had. */
    private static Optional<String> workspaceNameIn(Path file) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (WorkspaceArchiveFormat.MANIFEST.equals(entry.getName())) {
                    var name = new ObjectMapper().readTree(zip.readAllBytes()).path("name");
                    return name.isTextual() && !name.asText().isBlank()
                            ? Optional.of(name.asText())
                            : Optional.empty();
                }
            }
        } catch (IOException _) {
            // A damaged copy is still listed, by its file name.
        }
        return Optional.empty();
    }

    private static String nameFromFile(String id) {
        return id.substring(0, id.length() - WorkspaceArchiveFormat.FILE_EXTENSION.length());
    }

    private Path fileFor(Key key) {
        // A short digest of the session keeps two sessions' workspaces of one name apart.
        var digest = HexFormat.of().toHexDigits(key.sessionId().hashCode());
        var name = WorkspaceArchiveFormat.fileNameFor(key.workspaceName());
        return directory.resolve(
                nameFromFile(name) + "-" + digest + WorkspaceArchiveFormat.FILE_EXTENSION);
    }

    private void setAsidePreviousRun() {
        try {
            Files.createDirectories(previousRun);
            var left = new ArrayList<Path>();
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> FILE_NAME.matcher(nameOf(file)).matches())
                        .forEach(left::add);
            }
            for (var file : left) {
                Files.move(
                        file,
                        previousRun.resolve(nameOf(file)),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            if (!left.isEmpty()) {
                logger.info("{} unsaved workspace(s) kept from the previous run.", left.size());
            }
        } catch (IOException e) {
            logger.warn("Could not set aside the workspaces kept from the previous run.", e);
        }
    }

    private static void writeAtomically(Path file, byte[] content) throws IOException {
        var parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var temporary = file.resolveSibling(nameOf(file) + ".tmp");
        Files.write(temporary, content);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException _) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String nameOf(Path file) {
        var name = file.getFileName();
        return name == null ? "" : name.toString();
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Could not delete {}.", file, e);
        }
    }
}
