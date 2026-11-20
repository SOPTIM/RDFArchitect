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

import java.nio.file.Path;
import java.util.Map;

/**
 * Where the desktop app keeps its files: logs and the profile of its browser window. One directory
 * per user, where each operating system expects application data:
 *
 * <ul>
 *   <li>Windows: {@code %LOCALAPPDATA%\RDFArchitect}
 *   <li>macOS: {@code ~/Library/Application Support/RDFArchitect}
 *   <li>Linux: {@code $XDG_DATA_HOME/rdfarchitect}, by default {@code ~/.local/share/rdfarchitect}
 * </ul>
 */
final class DesktopPaths {

    private DesktopPaths() {}

    static Path dataDirectory(OperatingSystem os, Map<String, String> env, Path home) {
        return switch (os) {
            case WINDOWS -> {
                var localAppData = env.get("LOCALAPPDATA");
                var base =
                        isAbsolute(localAppData)
                                ? Path.of(localAppData)
                                : home.resolve("AppData").resolve("Local");
                yield base.resolve("RDFArchitect");
            }
            case MAC ->
                    home.resolve("Library").resolve("Application Support").resolve("RDFArchitect");
            case LINUX -> {
                var dataHome = env.get("XDG_DATA_HOME");
                var base =
                        isAbsolute(dataHome)
                                ? Path.of(dataHome)
                                : home.resolve(".local").resolve("share");
                yield base.resolve("rdfarchitect");
            }
        };
    }

    static Path logDirectory(Path dataDirectory) {
        return dataDirectory.resolve("logs");
    }

    static Path browserProfile(Path dataDirectory) {
        return dataDirectory.resolve("browser");
    }

    private static boolean isAbsolute(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return Path.of(path).isAbsolute();
        } catch (RuntimeException _) {
            return false;
        }
    }
}
