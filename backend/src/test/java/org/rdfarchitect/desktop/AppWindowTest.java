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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

class AppWindowTest {

    private static final Path HOME = Path.of("/home/someone");

    @Test
    void prefersChromeAndFallsBackToEdgeOnWindows() {
        var env =
                Map.of(
                        "ProgramFiles", "C:\\Program Files",
                        "ProgramFiles(x86)", "C:\\Program Files (x86)",
                        "LOCALAPPDATA", "C:\\Users\\someone\\AppData\\Local");
        var candidates = AppWindow.appBrowserCandidates(OperatingSystem.WINDOWS, env, HOME);
        var edge =
                Path.of(
                        "C:\\Program Files (x86)",
                        "Microsoft",
                        "Edge",
                        "Application",
                        "msedge.exe");

        assertThat(candidates.getFirst())
                .isEqualTo(
                        Path.of(
                                "C:\\Program Files",
                                "Google",
                                "Chrome",
                                "Application",
                                "chrome.exe"));
        assertThat(
                        AppWindow.findAppBrowser(
                                OperatingSystem.WINDOWS, env, HOME, Set.of(edge)::contains))
                .contains(edge);
    }

    @Test
    void looksInBothApplicationFoldersOnMac() {
        var chrome = HOME.resolve("Applications/Google Chrome.app/Contents/MacOS/Google Chrome");

        assertThat(
                        AppWindow.findAppBrowser(
                                OperatingSystem.MAC, Map.of(), HOME, Set.of(chrome)::contains))
                .contains(chrome);
    }

    @Test
    void searchesThePathOnLinux() {
        var chromium = Path.of("/opt/bin/chromium");

        assertThat(
                        AppWindow.findAppBrowser(
                                OperatingSystem.LINUX,
                                Map.of("PATH", "/usr/bin:/opt/bin"),
                                HOME,
                                Set.of(chromium)::contains))
                .contains(chromium);
        assertThat(
                        AppWindow.findAppBrowser(
                                OperatingSystem.LINUX,
                                Map.of("PATH", "/usr/bin"),
                                HOME,
                                _ -> false))
                .isEmpty();
    }

    @Test
    void opensAWindowOfItsOwnInAProfileOfItsOwn(@TempDir Path data) {
        var profile = data.resolve("browser");

        var command =
                AppWindow.appWindowCommand(
                        OperatingSystem.WINDOWS,
                        Path.of("msedge.exe"),
                        "http://127.0.0.1:17407/mainpage",
                        profile);

        assertThat(command)
                .startsWith("msedge.exe", "--app=http://127.0.0.1:17407/mainpage")
                .contains("--user-data-dir=" + profile, "--no-first-run", "--window-size=1440,900");
    }

    @Test
    void leavesTheWindowSizeToTheBrowserOnceItHasAProfile(@TempDir Path profile) {
        var command =
                AppWindow.appWindowCommand(
                        OperatingSystem.MAC, Path.of("chrome"), "http://x", profile);

        assertThat(command).noneMatch(argument -> argument.startsWith("--window-size"));
    }

    @Test
    void letsASnapUseItsOwnProfile(@TempDir Path dir) throws IOException {
        // Ubuntu's /usr/bin/chromium-browser is a script handing over to the snap.
        var wrapper =
                Files.writeString(
                        dir.resolve("chromium-browser"),
                        "#!/bin/sh\nexec /snap/bin/chromium \"$@\"\n");

        var command =
                AppWindow.appWindowCommand(
                        OperatingSystem.LINUX, wrapper, "http://x", dir.resolve("browser"));

        assertThat(AppWindow.snapName(wrapper)).contains("chromium");
        assertThat(command).noneMatch(argument -> argument.startsWith("--user-data-dir"));
        assertThat(command).contains("--class=RDFArchitect");
    }

    @Test
    void snapNameOf_pathsOfSnaps_namesTheSnap() {
        assertThat(AppWindow.snapNameOf(Path.of("/snap/bin/chromium"))).contains("chromium");
        assertThat(AppWindow.snapNameOf(Path.of("/snap/bin/chromium.chromedriver")))
                .contains("chromium");
        assertThat(
                        AppWindow.snapNameOf(
                                Path.of("/snap/chromium/3000/usr/lib/chromium-browser/chrome")))
                .contains("chromium");
        assertThat(AppWindow.snapNameOf(Path.of("/usr/bin/chromium"))).isEmpty();
        assertThat(AppWindow.snapNameOf(Path.of("/snap/bin"))).isEmpty();
    }

    @Test
    void snapName_linkToASnap_namesTheSnap(@TempDir Path dir) throws IOException {
        Path link;
        try {
            link =
                    Files.createSymbolicLink(
                            dir.resolve("chromium-browser"), Path.of("/snap/bin/chromium"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("No symbolic links here: " + e);
            return;
        }

        assertThat(AppWindow.snapName(link)).contains("chromium");
    }

    @Test
    void snapName_linkToTheSnapCommand_namesTheSnapAfterTheLink(@TempDir Path dir)
            throws IOException {
        Path link;
        try {
            link = Files.createSymbolicLink(dir.resolve("chromium"), Path.of("/usr/bin/snap"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("No symbolic links here: " + e);
            return;
        }

        assertThat(AppWindow.snapName(link)).contains("chromium");
    }

    @Test
    void snapName_browserOutsideSnaps_isEmpty(@TempDir Path dir) throws IOException {
        var chrome =
                Files.writeString(
                        dir.resolve("google-chrome"),
                        "#!/bin/sh\nexec /opt/google/chrome/chrome \"$@\"\n");

        assertThat(AppWindow.snapName(chrome)).isEmpty();
    }

    @Test
    void snapOfDesktopFile_desktopFileOfASnapTheUserHasRun_namesTheSnap(@TempDir Path home)
            throws IOException {
        Files.createDirectories(home.resolve("snap/firefox"));

        assertThat(AppWindow.snapOfDesktopFile("firefox_firefox.desktop", home))
                .contains("firefox");
        assertThat(AppWindow.snapOfDesktopFile("chromium_chromium.desktop", home)).isEmpty();
        assertThat(AppWindow.snapOfDesktopFile("firefox.desktop", home)).isEmpty();
        assertThat(AppWindow.snapOfDesktopFile("../firefox_firefox.desktop", home)).isEmpty();
        assertThat(AppWindow.snapOfDesktopFile("", home)).isEmpty();
    }

    @Test
    void snapLaunchDirectory_anySnap_isInTheSnapsOwnDirectory() {
        assertThat(AppWindow.snapLaunchDirectory(HOME, "firefox"))
                .isEqualTo(HOME.resolve("snap/firefox/common/rdfarchitect"));
    }

    @Test
    void opensTheDefaultBrowserTheWayEachSystemDoes() {
        assertThat(AppWindow.defaultBrowserCommand(OperatingSystem.MAC, "http://x"))
                .containsExactly("open", "http://x");
        assertThat(AppWindow.defaultBrowserCommand(OperatingSystem.LINUX, "http://x"))
                .containsExactly("xdg-open", "http://x");
        assertThat(AppWindow.defaultBrowserCommand(OperatingSystem.WINDOWS, "http://x"))
                .containsExactly("rundll32", "url.dll,FileProtocolHandler", "http://x");
    }
}
