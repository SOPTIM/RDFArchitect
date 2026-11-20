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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command line of the desktop app. Its own options are listed in {@link #USAGE}; any other
 * {@code --name=value} is handed to Spring, so every backend setting can be changed the usual way,
 * e.g. {@code --desktop.exit-when-closed=false}.
 *
 * @param port the port to listen on, {@code 0} for any free one
 * @param browser how to show the user interface
 * @param dataDirectory where the app keeps its files, or {@code null} for the default
 * @param help whether only the usage was asked for
 * @param springArguments the arguments for Spring
 * @param files workspace files to open, as passed by the operating system for a double-clicked file
 */
record DesktopOptions(
        int port,
        BrowserMode browser,
        Path dataDirectory,
        boolean help,
        List<String> springArguments,
        List<Path> files) {

    /**
     * The port the app prefers. Fixed, because the browser keeps the window's settings and storage
     * per origin, port included.
     */
    static final int DEFAULT_PORT = 17407;

    static final String USAGE =
            """
            Usage: RDFArchitect [options] [--<spring.property>=<value> ...] [<file.rdfa> ...]

              --port=<n>         Port to listen on, 0 for any free one (default @PORT@).
                                 Environment: RDFARCHITECT_PORT
              --browser=<mode>   How to show the user interface (default app):
                                   app      a window of its own, in Chrome, Edge, Brave or
                                            Chromium; the default browser if none is installed
                                   default  a tab of the default browser
                                   none     no window; open the printed address yourself
                                 Environment: RDFARCHITECT_BROWSER
              --data-dir=<dir>   Where to keep logs and the window's browser profile.
                                 Environment: RDFARCHITECT_DATA_DIR
              --help             Show this help.

            The app stops by itself once its last window has closed.
            """
                    .replace("@PORT@", Integer.toString(DEFAULT_PORT));

    /** How the user interface is shown. */
    enum BrowserMode {
        APP,
        DEFAULT,
        NONE
    }

    static DesktopOptions parse(String[] args, Map<String, String> env) {
        var port = parsePort(env.get("RDFARCHITECT_PORT"), DEFAULT_PORT);
        var browser = parseBrowser(env.get("RDFARCHITECT_BROWSER"), BrowserMode.APP);
        var dataDirectory = blankToNull(env.get("RDFARCHITECT_DATA_DIR"));
        var help = false;
        var springArguments = new ArrayList<String>();
        var files = new ArrayList<Path>();
        for (var arg : args) {
            if (arg.equals("--help") || arg.equals("-h") || arg.equals("/?")) {
                help = true;
            } else if (arg.startsWith("--port=")) {
                port = parsePort(valueOf(arg), port);
            } else if (arg.startsWith("--browser=")) {
                browser = parseBrowser(valueOf(arg), browser);
            } else if (arg.startsWith("--data-dir=")) {
                dataDirectory = blankToNull(valueOf(arg));
            } else if (arg.startsWith("-psn_")) {
                // The process serial number older macOS versions pass to an app started from
                // Finder.
                continue;
            } else if (arg.startsWith("--")) {
                springArguments.add(arg);
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else if (!arg.isBlank()) {
                files.add(Path.of(arg));
            }
        }
        return new DesktopOptions(
                port,
                browser,
                dataDirectory == null ? null : Path.of(dataDirectory),
                help,
                List.copyOf(springArguments),
                List.copyOf(files));
    }

    private static String valueOf(String arg) {
        return arg.substring(arg.indexOf('=') + 1);
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("Not a port: " + value);
            }
            return port;
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Not a port: " + value);
        }
    }

    private static BrowserMode parseBrowser(String value, BrowserMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return BrowserMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException(
                    "Unknown browser mode \"" + value + "\", expected app, default or none");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
