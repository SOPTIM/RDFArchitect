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

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Shows the user interface of the desktop app.
 *
 * <p>Preferably as a window of its own, the way an installed web app looks: a Chromium-based
 * browser started with {@code --app} shows the page without tabs or address bar. Edge comes with
 * every Windows 10 and 11, so on Windows there practically always is one. The browser gets a
 * profile of its own in the app's data directory, which keeps the app's storage apart from the
 * user's browsing and lets the window open even when that browser is already running. Without such
 * a browser, the page opens in a tab of the default browser.
 */
final class AppWindow {

    private static final Logger logger = LoggerFactory.getLogger(AppWindow.class);

    private static final String INITIAL_WINDOW_SIZE = "--window-size=1440,900";

    private AppWindow() {}

    /**
     * Opens the page.
     *
     * @param url the page to show
     * @param mode how to show it
     * @param browserProfile the profile directory for a window of its own
     * @return whether a browser was started
     */
    static boolean open(String url, DesktopOptions.BrowserMode mode, Path browserProfile) {
        if (mode == DesktopOptions.BrowserMode.NONE) {
            return false;
        }
        if (mode == DesktopOptions.BrowserMode.APP) {
            var os = OperatingSystem.current();
            var browser =
                    findAppBrowser(os, System.getenv(), Path.of(System.getProperty("user.home")));
            if (browser.isPresent()) {
                var command = appWindowCommand(os, browser.get(), url, browserProfile);
                try {
                    start(command);
                    logger.info("Opened the window with {}.", browser.get());
                    return true;
                } catch (IOException e) {
                    logger.warn("Could not start {}.", browser.get(), e);
                }
            } else {
                logger.info("No Chromium-based browser found, using the default browser.");
            }
        }
        return openInDefaultBrowser(url);
    }

    /** Returns the first Chromium-based browser installed, in order of preference. */
    static Optional<Path> findAppBrowser(OperatingSystem os, Map<String, String> env, Path home) {
        return findAppBrowser(os, env, home, AppWindow::isExecutable);
    }

    static Optional<Path> findAppBrowser(
            OperatingSystem os, Map<String, String> env, Path home, Predicate<Path> exists) {
        return appBrowserCandidates(os, env, home).stream().filter(exists).findFirst();
    }

    static List<Path> appBrowserCandidates(OperatingSystem os, Map<String, String> env, Path home) {
        var candidates = new ArrayList<Path>();
        switch (os) {
            case WINDOWS -> {
                var programFiles = env.get("ProgramFiles");
                var programFilesX86 = env.get("ProgramFiles(x86)");
                var localAppData = env.get("LOCALAPPDATA");
                for (var base : new String[] {programFiles, programFilesX86, localAppData}) {
                    addIfPresent(candidates, base, "Google", "Chrome", "Application", "chrome.exe");
                }
                for (var base : new String[] {programFilesX86, programFiles, localAppData}) {
                    addIfPresent(
                            candidates, base, "Microsoft", "Edge", "Application", "msedge.exe");
                }
                for (var base : new String[] {programFiles, localAppData}) {
                    addIfPresent(
                            candidates,
                            base,
                            "BraveSoftware",
                            "Brave-Browser",
                            "Application",
                            "brave.exe");
                }
                addIfPresent(candidates, localAppData, "Chromium", "Application", "chrome.exe");
            }
            case MAC -> {
                var folders = new ArrayList<Path>();
                var root = home.toAbsolutePath().getRoot();
                if (root != null) {
                    folders.add(root.resolve("Applications"));
                }
                folders.add(home.resolve("Applications"));
                for (var app :
                        new String[] {
                            "Google Chrome", "Microsoft Edge", "Brave Browser", "Chromium"
                        }) {
                    for (var applications : folders) {
                        candidates.add(
                                applications
                                        .resolve(app + ".app")
                                        .resolve("Contents")
                                        .resolve("MacOS")
                                        .resolve(app));
                    }
                }
            }
            case LINUX -> {
                var path = env.getOrDefault("PATH", "");
                for (var name :
                        new String[] {
                            "google-chrome",
                            "google-chrome-stable",
                            "chromium",
                            "chromium-browser",
                            "microsoft-edge",
                            "microsoft-edge-stable",
                            "brave-browser"
                        }) {
                    for (var directory : path.split(File.pathSeparator)) {
                        if (!directory.isBlank()) {
                            candidates.add(Path.of(directory, name));
                        }
                    }
                }
            }
        }
        return candidates;
    }

    static List<String> appWindowCommand(
            OperatingSystem os, Path browser, String url, Path browserProfile) {
        var command = new ArrayList<String>();
        command.add(browser.toString());
        command.add("--app=" + url);
        // A browser installed as a snap may not write to hidden directories in the home directory,
        // where the profile lives; it opens the window in its own profile instead.
        if (!(os == OperatingSystem.LINUX && isSnap(browser))) {
            if (!Files.isDirectory(browserProfile)) {
                // Only the first time: from then on the browser remembers where the user put it.
                command.add(INITIAL_WINDOW_SIZE);
            }
            command.add("--user-data-dir=" + browserProfile);
        }
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        if (os == OperatingSystem.LINUX) {
            command.add("--class=RDFArchitect");
        }
        return command;
    }

    static List<String> defaultBrowserCommand(OperatingSystem os, String url) {
        return switch (os) {
            case WINDOWS -> List.of("rundll32", "url.dll,FileProtocolHandler", url);
            case MAC -> List.of("open", url);
            case LINUX -> List.of("xdg-open", url);
        };
    }

    private static boolean openInDefaultBrowser(String url) {
        try {
            start(defaultBrowserCommand(OperatingSystem.current(), url));
            logger.info("Opened {} in the default browser.", url);
            return true;
        } catch (IOException e) {
            logger.debug("Could not start the default browser by command.", e);
        }
        if (!GraphicsEnvironment.isHeadless()
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                logger.info("Opened {} in the default browser.", url);
                return true;
            } catch (IOException | UnsupportedOperationException e) {
                logger.warn("Could not open the default browser.", e);
            }
        }
        return false;
    }

    private static void start(List<String> command) throws IOException {
        new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /**
     * Whether the browser runs confined as a snap: a link into {@code /snap}, or one of the scripts
     * Ubuntu puts in {@code /usr/bin} to hand over to the snap.
     */
    static boolean isSnap(Path browser) {
        try {
            var real = browser.toRealPath();
            if (real.startsWith("/snap/") || real.endsWith("snap")) {
                return true;
            }
            if (Files.size(real) > 64 * 1024) {
                return false;
            }
            return Files.readString(real, StandardCharsets.ISO_8859_1).contains("/snap/");
        } catch (IOException | RuntimeException _) {
            return browser.startsWith("/snap/");
        }
    }

    private static void addIfPresent(List<Path> candidates, String base, String... more) {
        if (base != null && !base.isBlank()) {
            candidates.add(Path.of(base, more));
        }
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }
}
