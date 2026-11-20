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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

class DesktopPathsTest {

    private static final Path HOME = Path.of("/home/someone");

    @Test
    void keepsItsFilesWhereEachOperatingSystemExpectsThem() {
        assertThat(DesktopPaths.dataDirectory(OperatingSystem.MAC, Map.of(), HOME))
                .isEqualTo(HOME.resolve("Library/Application Support/RDFArchitect"));
        assertThat(DesktopPaths.dataDirectory(OperatingSystem.LINUX, Map.of(), HOME))
                .isEqualTo(HOME.resolve(".local/share/rdfarchitect"));
        assertThat(
                        DesktopPaths.dataDirectory(
                                OperatingSystem.LINUX, Map.of("XDG_DATA_HOME", "/data"), HOME))
                .isEqualTo(Path.of("/data/rdfarchitect"));
        assertThat(DesktopPaths.dataDirectory(OperatingSystem.WINDOWS, Map.of(), HOME))
                .isEqualTo(HOME.resolve("AppData").resolve("Local").resolve("RDFArchitect"));
    }

    @Test
    void ignoresARelativeOverride() {
        assertThat(
                        DesktopPaths.dataDirectory(
                                OperatingSystem.LINUX, Map.of("XDG_DATA_HOME", "relative"), HOME))
                .isEqualTo(HOME.resolve(".local/share/rdfarchitect"));
    }

    @Test
    void keepsLogsAndTheBrowserProfileInside() {
        var data = Path.of("/data/rdfarchitect");

        assertThat(DesktopPaths.logDirectory(data)).isEqualTo(data.resolve("logs"));
        assertThat(DesktopPaths.browserProfile(data)).isEqualTo(data.resolve("browser"));
    }

    @Test
    void recognisesTheOperatingSystem() {
        assertThat(OperatingSystem.of("Windows 11")).isEqualTo(OperatingSystem.WINDOWS);
        assertThat(OperatingSystem.of("Mac OS X")).isEqualTo(OperatingSystem.MAC);
        assertThat(OperatingSystem.of("Linux")).isEqualTo(OperatingSystem.LINUX);
        assertThat(OperatingSystem.of("FreeBSD")).isEqualTo(OperatingSystem.LINUX);
    }
}
