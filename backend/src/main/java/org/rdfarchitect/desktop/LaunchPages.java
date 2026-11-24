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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Takes a browser into the desktop app without the access token on the browser's command line,
 * which other users of the computer can read. The browser opens a page in the user's data
 * directory, which only that user can read, and the page forwards it to the session address with
 * the token (see {@link DesktopAccessFilter}).
 *
 * <ul>
 *   <li>An app window opens {@value #APP_WINDOW_PAGE}, the same page for every window: Chromium
 *       tells app windows apart by the path of the address they start with, for example to group
 *       them in the Windows taskbar. The page to show is in the fragment of the address.
 *   <li>The default browser may drop the fragment of a file address, so each of its windows gets a
 *       page of its own, which is deleted a few minutes later.
 * </ul>
 */
final class LaunchPages {

    static final String APP_WINDOW_PAGE = "launch.html";

    private static final String WINDOW_PAGE_PREFIX = "open-";
    private static final String PAGE_SUFFIX = ".html";
    private static final Duration WINDOW_PAGE_LIFETIME = Duration.ofMinutes(5);

    private static final String PAGE =
            """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="referrer" content="no-referrer">
            <title>RDFArchitect</title>
            <script>
            {script}</script>
            <noscript><meta http-equiv="refresh" content="0; url={noScriptUrl}"></noscript>
            </head>
            <body><p>Opening RDFArchitect…</p></body>
            </html>
            """;

    private static final String APP_WINDOW_SCRIPT =
            """
            var next = "{startPage}";
            try {
              next = decodeURIComponent(location.hash.substring(1)) || next;
            } catch (e) {}
            location.replace("{sessionBase}" + encodeURIComponent(next));
            """;

    private static final Logger logger = LoggerFactory.getLogger(LaunchPages.class);

    private final Path directory;
    private final int port;
    private final DesktopAccessToken token;

    /**
     * @param directory where to write the pages, readable by the user only
     * @param port the port of the app
     * @param token the access token of the app
     */
    LaunchPages(Path directory, int port, DesktopAccessToken token) {
        this.directory = directory;
        this.port = port;
        this.token = token;
    }

    /**
     * Returns the address that lets a browser in and shows a page of the app.
     *
     * @param next the path of the page, such as {@code /mainpage}
     * @return the address, with the token in it
     */
    String sessionUrl(String next) {
        return sessionBase() + encode(next);
    }

    /**
     * Writes the page for app windows, unless it is there already.
     *
     * @param next the path of the page to show
     * @return the address of the page, with {@code next} in its fragment
     * @throws IOException if the page cannot be written
     */
    URI appWindowPage(String next) throws IOException {
        var page = directory.resolve(APP_WINDOW_PAGE);
        var content = appWindowPageContent();
        if (!content.equals(readIfPresent(page))) {
            PrivateFiles.write(page, content);
        }
        return URI.create(page.toUri().toASCIIString() + "#" + encode(next));
    }

    /**
     * Writes a page for one window of the default browser, and deletes the ones written a while
     * ago.
     *
     * @param next the path of the page to show
     * @return the address of the page
     * @throws IOException if the page cannot be written
     */
    URI windowPage(String next) throws IOException {
        deleteOldWindowPages();
        var page = directory.resolve(WINDOW_PAGE_PREFIX + UUID.randomUUID() + PAGE_SUFFIX);
        PrivateFiles.write(page, windowPageContent(sessionUrl(next)));
        return page.toUri();
    }

    private String sessionBase() {
        return LocalPorts.baseUrl(port)
                + DesktopAccessFilter.SESSION_PATH
                + "?"
                + DesktopAccessFilter.TOKEN_PARAMETER
                + "="
                + token.value()
                + "&"
                + DesktopAccessFilter.NEXT_PARAMETER
                + "=";
    }

    /**
     * The token is base64url and {@link #encode} leaves only letters, digits, {@code %} and {@code
     * -._*}, so neither needs escaping in the page beyond {@code &} in an attribute.
     */
    private String appWindowPageContent() {
        var script =
                APP_WINDOW_SCRIPT
                        .replace("{startPage}", DesktopAccessFilter.START_PAGE)
                        .replace("{sessionBase}", sessionBase());
        return page(script, sessionUrl(DesktopAccessFilter.START_PAGE));
    }

    private static String windowPageContent(String sessionUrl) {
        return page("location.replace(\"" + sessionUrl + "\");\n", sessionUrl);
    }

    private static String page(String script, String noScriptUrl) {
        return PAGE.replace("{script}", script)
                .replace("{noScriptUrl}", noScriptUrl.replace("&", "&amp;"));
    }

    private void deleteOldWindowPages() {
        var oldest = Instant.now().minus(WINDOW_PAGE_LIFETIME);
        try (var pages =
                Files.newDirectoryStream(directory, WINDOW_PAGE_PREFIX + "*" + PAGE_SUFFIX)) {
            for (var page : pages) {
                if (Files.getLastModifiedTime(page).toInstant().isBefore(oldest)) {
                    Files.deleteIfExists(page);
                }
            }
        } catch (IOException e) {
            logger.debug("Cannot delete old pages in {}.", directory, e);
        }
    }

    private static String readIfPresent(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException _) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
