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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

class PrivateFilesTest {

    @TempDir Path directory;

    @BeforeEach
    void requirePosix() {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    }

    @Test
    void createDirectory_missing_createsItAndItsParentsForTheOwnerOnly() throws IOException {
        var created = PrivateFiles.createDirectory(directory.resolve("data/rdfarchitect"));

        assertThat(created).isDirectory();
        assertThat(permissions(created)).isEqualTo("rwx------");
    }

    @Test
    void createDirectory_existingOpenToOthers_restrictsItToTheOwner() throws IOException {
        var existing =
                Files.createDirectory(
                        directory.resolve("data"),
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwxr-xr-x")));
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("rwxrwxrwx"));

        PrivateFiles.createDirectory(existing);

        assertThat(permissions(existing)).isEqualTo("rwx------");
    }

    @Test
    void write_existingFile_replacesItForTheOwnerOnly() throws IOException {
        var file = Files.writeString(directory.resolve("instance.json"), "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        PrivateFiles.write(file, "new");

        assertThat(file).hasContent("new");
        assertThat(permissions(file)).isEqualTo("rw-------");
        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(file);
        }
    }

    private static String permissions(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }
}
