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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Shows the user interface of the desktop app.
 *
 * <p>Preferably as a window of its own, the way an installed web app looks: a Chromium-based
 * browser started with {@code --app} shows the page without tabs or address bar. Edge comes with
 * every Windows 10 and 11, so on Windows there practically always is one. The browser gets a
 * profile of its own in the app's data directory, which keeps the app's storage apart from the
 * user's browsing and lets the window open even when that browser is already running. Without such
 * a browser, the page opens in a tab of the default browser. Either way the browser starts with a
 * page in the app's data directory, which lets it in (see {@link LaunchPages}).
 */
final class AppWindow {

    private static final Logger logger = LoggerFactory.getLogger(AppWindow.class);

    private static final String INITIAL_WINDOW_SIZE = "--window-size=1440,900";

    private static final Pattern SNAP_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern SNAP_COMMAND = Pattern.compile("/snap/bin/([a-z0-9][a-z0-9-]*)");
    private static final int MAX_LINKS = 10;

    private AppWindow() {}

    /**
     * Opens a page of the app, by way of a launch page (see {@link LaunchPages}). With {@link
     * DesktopOptions.BrowserMode#NONE} it prints the address to open instead, with the token in it.
     *
     * @param pages the launch pages of the app
     * @param next the path of the page to show, such as {@code /mainpage}
     * @param mode how to show it
     * @param browserProfile the profile directory for a window of its own
     * @return whether a browser was started
     * @throws IOException if the launch page cannot be written
     */
    static boolean open(
            LaunchPages pages, String next, DesktopOptions.BrowserMode mode, Path browserProfile)
            throws IOException {
        if (mode == DesktopOptions.BrowserMode.NONE) {
            // Standard output, not the log: only the user who started the app sees it.
            System.out.println("RDFArchitect is running at " + pages.sessionUrl(next));
            return false;
        }
        var os = OperatingSystem.current();
        var home = Path.of(System.getProperty("user.home"));
        if (mode == DesktopOptions.BrowserMode.APP) {
            var browser = findAppBrowser(os, System.getenv(), home);
            if (browser.isPresent()) {
                var snap =
                        os == OperatingSystem.LINUX
                                ? snapName(browser.get())
                                : Optional.<String>empty();
                var page = readableBy(snap, pages, home).appWindowPage(next).toASCIIString();
                var command = appWindowCommand(os, browser.get(), page, browserProfile);
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
        var snap =
                os == OperatingSystem.LINUX ? defaultBrowserSnap(home) : Optional.<String>empty();
        return openInDefaultBrowser(readableBy(snap, pages, home).windowPage(next).toASCIIString());
    }

    /**
     * Returns launch pages the browser can read. A browser installed as a snap cannot read hidden
     * directories in the home directory, where the data directory is, but it can read its own
     * directory in {@code ~/snap}.
     */
    private static LaunchPages readableBy(Optional<String> snap, LaunchPages pages, Path home)
            throws IOException {
        if (snap.isEmpty()) {
            return pages;
        }
        return pages.in(PrivateFiles.createDirectory(snapLaunchDirectory(home, snap.get())));
    }

    static Path snapLaunchDirectory(Path home, String snap) {
        return home.resolve("snap").resolve(snap).resolve("common").resolve("rdfarchitect");
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
        if (!(os == OperatingSystem.LINUX && snapName(browser).isPresent())) {
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
     * Returns the name of the snap the browser runs confined in, if it does: a link to {@code
     * /snap/bin/<name>}, a file in {@code /snap/<name>/}, or one of the scripts Ubuntu puts in
     * {@code /usr/bin} to hand over to a snap.
     */
    static Optional<String> snapName(Path browser) {
        try {
            var path = browser.toAbsolutePath();
            for (var links = 0; links < MAX_LINKS; links++) {
                var name = snapNameOf(path);
                if (name.isPresent() || !Files.isSymbolicLink(path)) {
                    return name.or(() -> snapNameInScript(browser));
                }
                var target = path.resolveSibling(Files.readSymbolicLink(path)).normalize();
                // The snap command runs the app named like the link it is started through.
                if (target.endsWith("snap")) {
                    return snapOfApp(String.valueOf(path.getFileName()));
                }
                path = target;
            }
            return Optional.empty();
        } catch (IOException | RuntimeException _) {
            return snapNameOf(browser);
        }
    }

    /**
     * Returns the snap named by a path in {@code /snap/bin/<name>[.<app>]} or {@code
     * /snap/<name>/}.
     */
    static Optional<String> snapNameOf(Path path) {
        if (!path.startsWith("/snap") || path.getNameCount() < 3) {
            return Optional.empty();
        }
        var second = path.getName(1).toString();
        return snapOfApp(second.equals("bin") ? path.getName(2).toString() : second);
    }

    /** Returns the snap of an app named {@code <snap>} or {@code <snap>.<app>}. */
    private static Optional<String> snapOfApp(String app) {
        var dot = app.indexOf('.');
        return validSnapName(dot < 0 ? app : app.substring(0, dot));
    }

    private static Optional<String> snapNameInScript(Path browser) {
        try {
            var real = browser.toRealPath();
            if (Files.size(real) > 64 * 1024) {
                return Optional.empty();
            }
            var matcher = SNAP_COMMAND.matcher(Files.readString(real, StandardCharsets.ISO_8859_1));
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        }
    }

    /** Returns the snap of the default browser, if it is one. Linux only. */
    private static Optional<String> defaultBrowserSnap(Path home) {
        try {
            var process =
                    new ProcessBuilder("xdg-settings", "get", "default-web-browser")
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                return Optional.empty();
            }
            var desktopFile =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return snapOfDesktopFile(desktopFile.strip(), home);
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Returns the snap a desktop file belongs to: snapd names them {@code <snap>_<app>.desktop},
     * and a snap the user has run has a directory in {@code ~/snap}.
     */
    static Optional<String> snapOfDesktopFile(String desktopFile, Path home) {
        var separator = desktopFile.indexOf('_');
        if (separator <= 0 || !desktopFile.endsWith(".desktop")) {
            return Optional.empty();
        }
        return validSnapName(desktopFile.substring(0, separator))
                .filter(snap -> Files.isDirectory(home.resolve("snap").resolve(snap)));
    }

    private static Optional<String> validSnapName(String name) {
        return SNAP_NAME.matcher(name).matches() ? Optional.of(name) : Optional.empty();
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
