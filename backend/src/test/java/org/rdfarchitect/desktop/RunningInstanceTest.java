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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

class RunningInstanceTest {

    @TempDir Path directory;

    private final RunningInstance instance = new RunningInstance(17407, "secret", "1.9.0");

    @Test
    void read_writtenInstance_returnsIt() throws Exception {
        instance.write(directory);

        assertThat(RunningInstance.read(directory)).contains(instance);
        assertThat(directory.resolve("instance.json")).isRegularFile();
    }

    @Test
    void write_posixFileSystem_isReadableByTheOwnerOnly() throws Exception {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));

        instance.write(directory);

        assertThat(
                        PosixFilePermissions.toString(
                                Files.getPosixFilePermissions(directory.resolve("instance.json"))))
                .isEqualTo("rw-------");
    }

    @Test
    void read_missingOrDamagedFile_returnsNothing() throws Exception {
        assertThat(RunningInstance.read(directory)).isEmpty();

        Files.writeString(directory.resolve("instance.json"), "{\"port\":17");
        assertThat(RunningInstance.read(directory)).isEmpty();

        Files.writeString(directory.resolve("instance.json"), "{\"port\":0,\"token\":\"t\"}");
        assertThat(RunningInstance.read(directory)).isEmpty();

        Files.writeString(directory.resolve("instance.json"), "{\"port\":17407}");
        assertThat(RunningInstance.read(directory)).isEmpty();
    }

    @Test
    void read_fileOfAnotherVersionWithMoreFields_returnsTheInstance() throws Exception {
        Files.writeString(
                directory.resolve("instance.json"),
                "{\"port\":17407,\"token\":\"secret\",\"version\":\"1.9.0\",\"pid\":42}");

        assertThat(RunningInstance.read(directory)).contains(instance);
    }

    @Test
    void deleteFrom_ownFile_deletesIt() throws Exception {
        instance.write(directory);

        instance.deleteFrom(directory);

        assertThat(directory.resolve("instance.json")).doesNotExist();
    }

    @Test
    void deleteFrom_fileOfAnotherInstance_keepsIt() throws Exception {
        var newer = new RunningInstance(52000, "other", "2.0.0");
        newer.write(directory);

        instance.deleteFrom(directory);

        assertThat(RunningInstance.read(directory)).contains(newer);
    }

    @Test
    void toString_always_hidesTheToken() {
        assertThat(instance.toString()).contains("17407").doesNotContain("secret");
    }
}
