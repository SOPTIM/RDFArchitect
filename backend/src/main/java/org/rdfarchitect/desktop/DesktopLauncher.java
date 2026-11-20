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

import java.awt.GraphicsEnvironment;
import java.awt.SplashScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import javax.swing.JOptionPane;

/**
 * Starts RDFArchitect as a desktop app: the backend on the loopback interface of the user's own
 * machine, serving the user interface, which then opens in a window of its own (see {@link
 * AppWindow}). The main class of the desktop build, see the {@code desktop} Maven profile.
 *
 * <p>Only one app runs per user and version: if the same version already listens on the port, a
 * second start just opens another window of it.
 */
public final class DesktopLauncher {

    /** Read by {@code log4j2-desktop.yaml}. */
    static final String LOG_DIRECTORY_PROPERTY = "rdfarchitect.desktop.log-dir";

    private static final String LOG_CONFIGURATION = "log4j2-desktop.yaml";

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

        var logger = LogManager.getLogger(DesktopLauncher.class);
        try {
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
        var version = new AppVersionResolver(System.getenv("APP_VERSION")).resolveVersion();
        var port = options.port();
        if (port != 0 && !LocalPorts.isFree(port)) {
            var running = LocalPorts.desktopAppVersion(port);
            if (running.isPresent() && running.get().equals(version)) {
                logger.info("RDFArchitect {} is running already, opening a window.", version);
                closeSplash();
                AppWindow.open(
                        startPage(port),
                        options.browser(),
                        DesktopPaths.browserProfile(dataDirectory));
                return false;
            }
            logger.info(
                    "Port {} is taken by {}, using another one.",
                    port,
                    running.map(v -> "RDFArchitect " + v).orElse("another program"));
            port = LocalPorts.anyFreePort();
        } else if (port == 0) {
            port = LocalPorts.anyFreePort();
        }

        var application = new SpringApplication(Launcher.class);
        application.setAdditionalProfiles("desktop");
        try {
            application.run(springArguments(options, port));
        } catch (RuntimeException e) {
            if (!isPortInUse(e)) {
                throw new IllegalStateException(rootMessage(e), e);
            }
            // Started twice in quick succession: both found the port free, the other one got it.
            if (awaitDesktopApp(port, version).isEmpty()) {
                throw new IllegalStateException("Port " + port + " is in use.", e);
            }
            logger.info("RDFArchitect {} started meanwhile, opening a window.", version);
            closeSplash();
            AppWindow.open(
                    startPage(port), options.browser(), DesktopPaths.browserProfile(dataDirectory));
            return false;
        }

        var url = startPage(port);
        logger.info("RDFArchitect {} is running at {}", version, url);
        System.out.println("RDFArchitect is running at " + url);
        AppWindow.open(url, options.browser(), DesktopPaths.browserProfile(dataDirectory));
        closeSplash();
        return true;
    }

    private static String rootMessage(Throwable failure) {
        var root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.toString();
    }

    static boolean isPortInUse(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PortInUseException) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> awaitDesktopApp(int port, String version) {
        var deadline = Instant.now().plus(STARTUP_OF_ANOTHER_INSTANCE);
        while (Instant.now().isBefore(deadline)) {
            var running = LocalPorts.desktopAppVersion(port).filter(version::equals);
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

    static String startPage(int port) {
        return LocalPorts.baseUrl(port) + "/mainpage";
    }

    static String[] springArguments(DesktopOptions options, int port) {
        var arguments = new ArrayList<String>();
        arguments.add("--server.port=" + port);
        // Cookies are kept per host, not per port: a second backend on this machine, such as a
        // development server, would otherwise take over the session.
        arguments.add("--server.servlet.session.cookie.name=RDFA_DESKTOP_SESSION_" + port);
        arguments.add("--logging.config=classpath:" + LOG_CONFIGURATION);
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
