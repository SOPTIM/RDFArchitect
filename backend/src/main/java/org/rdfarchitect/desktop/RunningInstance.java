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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The desktop app running for a data directory, as it describes itself in {@code
 * run/instance.json}, readable by the user only. A later start reads it to open a window of the app
 * instead of starting another one, once the app has proved to hold the token (see {@link
 * LocalPorts#provenVersion}): the file may be left over from a run that ended without removing it.
 *
 * @param port the port it listens on
 * @param token its access token
 * @param version its version
 */
record RunningInstance(int port, String token, String version) {

    static final String FILE_NAME = "instance.json";

    private static final Logger logger = LoggerFactory.getLogger(RunningInstance.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    DesktopAccessToken accessToken() {
        return new DesktopAccessToken(token);
    }

    /**
     * Reads the file in the directory.
     *
     * @param runDirectory the directory
     * @return the instance it describes, empty if there is none or it cannot be read
     */
    static Optional<RunningInstance> read(Path runDirectory) {
        var file = runDirectory.resolve(FILE_NAME);
        try {
            var instance =
                    MAPPER.readValue(
                            Files.readString(file, StandardCharsets.UTF_8), RunningInstance.class);
            if (instance.port() <= 0
                    || instance.port() > 65_535
                    || instance.token() == null
                    || instance.token().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(instance);
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            logger.debug("Cannot read {}.", file, e);
            return Optional.empty();
        }
    }

    /**
     * Writes the file in the directory.
     *
     * @param runDirectory the directory
     * @throws IOException if it cannot be written
     */
    void write(Path runDirectory) throws IOException {
        PrivateFiles.write(runDirectory.resolve(FILE_NAME), MAPPER.writeValueAsString(this));
    }

    /**
     * Deletes the file in the directory, unless it describes another instance by now: a newer
     * version may have started meanwhile, on another port.
     *
     * @param runDirectory the directory
     */
    void deleteFrom(Path runDirectory) {
        if (!read(runDirectory).map(this::equals).orElse(false)) {
            return;
        }
        try {
            Files.deleteIfExists(runDirectory.resolve(FILE_NAME));
        } catch (IOException e) {
            logger.warn("Cannot delete {}.", runDirectory.resolve(FILE_NAME), e);
        }
    }

    @Override
    public String toString() {
        return "RunningInstance[port=" + port + ", version=" + version + "]";
    }
}
