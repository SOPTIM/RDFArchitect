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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Directories and files only the user running the app may read, where the file system has POSIX
 * permissions: {@code 0700} and {@code 0600}. On Windows the data directory is in the user's own
 * profile, which other users cannot read.
 */
final class PrivateFiles {

    private static final Logger logger = LoggerFactory.getLogger(PrivateFiles.class);

    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE =
            PosixFilePermissions.fromString("rw-------");

    private PrivateFiles() {}

    /**
     * Creates the directory, readable by its owner only, or takes that right from everyone else if
     * it exists. Creates missing parents the usual way.
     *
     * @param directory the directory
     * @return the directory
     * @throws IOException if it cannot be created
     */
    static Path createDirectory(Path directory) throws IOException {
        if (!hasPosixPermissions(directory)) {
            return Files.createDirectories(directory);
        }
        var parent = directory.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.createDirectory(
                    directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY));
        } catch (FileAlreadyExistsException _) {
            if (!Files.isDirectory(directory)) {
                throw new FileAlreadyExistsException(directory + " is not a directory");
            }
            try {
                if (!Files.getPosixFilePermissions(directory).equals(OWNER_ONLY_DIRECTORY)) {
                    Files.setPosixFilePermissions(directory, OWNER_ONLY_DIRECTORY);
                }
            } catch (IOException | UnsupportedOperationException e) {
                logger.warn("Cannot make {} readable by its owner only.", directory, e);
            }
        }
        return directory;
    }

    /**
     * Replaces the file with the content in one step, so that a reader never sees half of it.
     *
     * @param file the file
     * @param content its new content
     * @throws IOException if it cannot be written
     */
    static void write(Path file, String content) throws IOException {
        var absolute = file.toAbsolutePath();
        var directory = absolute.getParent();
        var name = absolute.getFileName();
        if (directory == null || name == null) {
            throw new IOException("Not a file: " + file);
        }
        var temporary = Files.createTempFile(directory, "." + name, ".tmp", ownerOnly(directory));
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static FileAttribute<?>[] ownerOnly(Path directory) {
        return hasPosixPermissions(directory)
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE)}
                : new FileAttribute<?>[0];
    }

    private static boolean hasPosixPermissions(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }
}
