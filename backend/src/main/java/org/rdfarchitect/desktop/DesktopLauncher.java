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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rdfarchitect.Launcher;
import org.rdfarchitect.config.AppVersionResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.awt.GraphicsEnvironment;
import java.awt.SplashScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import javax.swing.JOptionPane;

/**
 * Starts RDFArchitect as a desktop app: the backend on the loopback interface of the user's own
 * machine, serving the user interface, which then opens in a window of its own (see {@link
 * AppWindow}). The main class of the desktop build, see the {@code desktop} Maven profile.
 *
 * <p>Only one app runs per data directory: if one already runs, a second start just opens another
 * window of it, also when the two are of different versions. Only the user who started the app can
 * use it, see {@link DesktopAccessToken}.
 */
public final class DesktopLauncher {

    /** Read by {@code log4j2-desktop.yaml}. */
    static final String LOG_DIRECTORY_PROPERTY = "rdfarchitect.desktop.log-dir";

    private static final String LOG_CONFIGURATION = "log4j2-desktop.yaml";

    private static final String APP_VERSION = "APP_VERSION";

    /** Written by the {@code desktop} Maven profile. */
    private static final String VERSION_RESOURCE = "rdfarchitect-desktop.properties";

    private static final Duration STARTUP_OF_ANOTHER_INSTANCE = Duration.ofSeconds(60);

    private DesktopLauncher() {}

    public static void main(String[] args) {
        DesktopOptions options;
        try {
            options = DesktopOptions.parse(args, System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(DesktopOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.println(DesktopOptions.USAGE);
            return;
        }

        var dataDirectory =
                options.dataDirectory() != null
                        ? options.dataDirectory().toAbsolutePath()
                        : DesktopPaths.dataDirectory(
                                OperatingSystem.current(),
                                System.getenv(),
                                Path.of(System.getProperty("user.home")));
        var logDirectory = DesktopPaths.logDirectory(dataDirectory);
        // Before anything logs: Log4j reads its configuration once, on first use.
        System.setProperty(LOG_DIRECTORY_PROPERTY, logDirectory.toString());
        System.setProperty("log4j2.configurationFile", LOG_CONFIGURATION);

        // Read by AppVersionResolver like the APP_VERSION of the container images.
        if (configuredVersion().isBlank()) {
            bundledVersion().ifPresent(version -> System.setProperty(APP_VERSION, version));
        }

        // Before anything else touches AWT: macOS sends the files to open as events.
        OpenFileEvents.install();

        var logger = LogManager.getLogger(DesktopLauncher.class);
        try {
            PrivateFiles.createDirectory(dataDirectory);
            Files.createDirectories(logDirectory);
            if (!run(options, dataDirectory, logger)) {
                // Another instance shows the window; nothing is left to do here.
                System.exit(0);
            }
        } catch (Exception e) {
            logger.fatal("RDFArchitect could not start.", e);
            closeSplash();
            showError(
                    "RDFArchitect could not start:\n"
                            + e.getMessage()
                            + "\n\nThe log is in "
                            + logDirectory);
            System.exit(1);
        }
    }

    /**
     * Starts the app, or opens a window of the one already running.
     *
     * @return whether the app runs in this process
     */
    private static boolean run(DesktopOptions options, Path dataDirectory, Logger logger)
            throws IOException {
        var version = new AppVersionResolver(configuredVersion()).resolveVersion();
        var browserProfile = DesktopPaths.browserProfile(dataDirectory);
        var runDirectory = PrivateFiles.createDirectory(DesktopPaths.runDirectory(dataDirectory));
        var running = findRunning(runDirectory);
        if (running.isPresent()) {
            logger.info(
                    "RDFArchitect {} is running already, opening a window of it instead of"
                            + " starting {}.",
                    running.get().version(),
                    version);
            handOver(running.get(), options, runDirectory, browserProfile, logger);
            return false;
        }
        var port = options.port();
        if (port != 0 && !LocalPorts.isFree(port)) {
            logger.info("Port {} is taken, using another one.", port);
            port = LocalPorts.anyFreePort();
        } else if (port == 0) {
            port = LocalPorts.anyFreePort();
        }

        var application = new SpringApplication(Launcher.class);
        application.setAdditionalProfiles("desktop");
        // Spring makes the JVM headless by default, after which the splash screen can no longer be
        // closed and stays up until the app ends.
        application.setHeadless(!hasDisplay(OperatingSystem.current(), System.getenv()));
        ConfigurableApplicationContext context;
        try {
            context = application.run(springArguments(options, port, dataDirectory));
        } catch (RuntimeException e) {
            if (!isPortInUse(e)) {
                throw new IllegalStateException(rootMessage(e), e);
            }
            // Started twice in quick succession: both found the port free, the other one got it.
            var other = awaitDesktopApp(runDirectory);
            if (other.isEmpty()) {
                throw new IllegalStateException("Port " + port + " is in use.", e);
            }
            logger.info(
                    "RDFArchitect {} started meanwhile, opening a window of it.",
                    other.get().version());
            handOver(other.get(), options, runDirectory, browserProfile, logger);
            return false;
        }

        var token = context.getBean(DesktopAccessToken.class);
        var instance = new RunningInstance(port, token.value(), version);
        instance.write(runDirectory);
        context.addApplicationListener(
                event -> {
                    if (event instanceof ContextClosedEvent) {
                        instance.deleteFrom(runDirectory);
                    }
                });

        var pages = new LaunchPages(runDirectory, port, token);
        var openRequests = context.getBean(OpenRequests.class);
        Consumer<List<Path>> openInNewWindow =
                files -> {
                    try {
                        AppWindow.open(
                                pages,
                                startPath(register(files, openRequests, logger)),
                                options.browser(),
                                browserProfile);
                    } catch (IOException e) {
                        logger.error("Could not open a window for {}.", files, e);
                    }
                };
        var files = new ArrayList<>(options.files());
        files.addAll(OpenFileEvents.takeAndForward(openInNewWindow));

        logger.info("RDFArchitect {} is running at {}", version, LocalPorts.baseUrl(port));
        AppWindow.open(
                pages,
                startPath(register(files, openRequests, logger)),
                options.browser(),
                browserProfile);
        closeSplash();
        return true;
    }

    /** Opens a window of the app already running, with the files to open in it. */
    private static void handOver(
            RunningInstance running,
            DesktopOptions options,
            Path runDirectory,
            Path browserProfile,
            Logger logger)
            throws IOException {
        var token = running.accessToken();
        var ids = new ArrayList<String>();
        for (var file : options.files()) {
            LocalPorts.requestOpen(running.port(), token, file)
                    .ifPresentOrElse(
                            ids::add, () -> logger.warn("Could not hand over {} to open.", file));
        }
        closeSplash();
        AppWindow.open(
                new LaunchPages(runDirectory, running.port(), token),
                startPath(ids),
                options.browser(),
                browserProfile);
    }

    private static List<String> register(
            List<Path> files, OpenRequests openRequests, Logger logger) {
        var ids = new ArrayList<String>();
        for (var file : files) {
            try {
                ids.add(openRequests.add(file).id());
            } catch (IllegalArgumentException e) {
                logger.warn("Not opening {}: {}", file, e.getMessage());
            }
        }
        return ids;
    }

    private static String rootMessage(Throwable failure) {
        var root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.toString();
    }

    /** Whether there is a display to show the splash screen and dialogs on. */
    static boolean hasDisplay(OperatingSystem os, Map<String, String> env) {
        if (os != OperatingSystem.LINUX) {
            return true;
        }
        return isSet(env.get("DISPLAY")) || isSet(env.get("WAYLAND_DISPLAY"));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    static boolean isPortInUse(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PortInUseException) {
                return true;
            }
        }
        return false;
    }

    /** Returns the app running for the data directory, of whatever version, if one answers. */
    static Optional<RunningInstance> findRunning(Path runDirectory) {
        return RunningInstance.read(runDirectory)
                .filter(
                        instance ->
                                LocalPorts.provenVersion(instance.port(), instance.accessToken())
                                        .isPresent());
    }

    /** Waits for the app another start is starting for the data directory. */
    private static Optional<RunningInstance> awaitDesktopApp(Path runDirectory) {
        var deadline = Instant.now().plus(STARTUP_OF_ANOTHER_INSTANCE);
        while (Instant.now().isBefore(deadline)) {
            var running = findRunning(runDirectory);
            if (running.isPresent()) {
                return running;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String configuredVersion() {
        var property = System.getProperty(APP_VERSION);
        if (property != null && !property.isBlank()) {
            return property;
        }
        var variable = System.getenv(APP_VERSION);
        return variable != null ? variable : "";
    }

    /**
     * Returns the version the desktop build was made for. Taken from the build rather than from
     * {@code git.properties}, which cannot name the lightweight release tags.
     */
    static Optional<String> bundledVersion() {
        try (var in =
                DesktopLauncher.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                return Optional.empty();
            }
            var properties = new Properties();
            properties.load(in);
            var version = properties.getProperty("version", "").strip();
            // Unfiltered, or a build that was not given a version.
            if (version.isEmpty() || version.contains("@") || version.equals("0.0.0")) {
                return Optional.empty();
            }
            return Optional.of(version);
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    /** The path of the editor, told to open the files of the given open requests. */
    static String startPath(List<String> openRequestIds) {
        return openRequestIds.isEmpty()
                ? DesktopAccessFilter.START_PAGE
                : DesktopAccessFilter.START_PAGE + "?open=" + String.join(",", openRequestIds);
    }

    static String[] springArguments(DesktopOptions options, int port, Path dataDirectory) {
        var arguments = new ArrayList<String>();
        arguments.add("--server.port=" + port);
        // Cookies are kept per host, not per port: a second backend on this machine, such as a
        // development server, would otherwise take over the session.
        arguments.add("--server.servlet.session.cookie.name=RDFA_DESKTOP_SESSION_" + port);
        arguments.add("--logging.config=classpath:" + LOG_CONFIGURATION);
        arguments.add("--desktop.data-directory=" + dataDirectory);
        arguments.addAll(options.springArguments());
        return arguments.toArray(String[]::new);
    }

    private static void closeSplash() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            var splash = SplashScreen.getSplashScreen();
            if (splash != null && splash.isVisible()) {
                splash.close();
            }
        } catch (UnsupportedOperationException | IllegalStateException _) {
            // No splash screen to close.
        }
    }

    private static void showError(String message) {
        System.err.println(message);
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            JOptionPane.showMessageDialog(null, message, "RDFArchitect", JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException _) {
            // Nothing more can be done without a display.
        }
    }
}
