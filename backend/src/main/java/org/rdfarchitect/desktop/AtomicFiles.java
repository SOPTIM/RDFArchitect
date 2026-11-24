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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Writes files so that they hold either their old or their complete new content. */
final class AtomicFiles {

    /** Ends the name of a file that is still being written. */
    static final String TEMPORARY_SUFFIX = ".tmp";

    private AtomicFiles() {}

    /**
     * Writes the content to a file of its own next to the target, forces it to the disk and then
     * moves it over the target. A crash or power loss leaves the old file, never an empty or
     * partial one, and two writes at once never mix.
     *
     * @param file the file to write
     * @param content what it is to hold
     * @throws IOException if the file cannot be written
     */
    static void write(Path file, byte[] content) throws IOException {
        var parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var temporary =
                file.resolveSibling(
                        file.getFileName() + "." + UUID.randomUUID() + TEMPORARY_SUFFIX);
        try {
            try (var channel =
                    FileChannel.open(
                            temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                // Some file systems, such as network shares, cannot replace a file atomically.
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
