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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Files macOS asks the app to open. macOS does not pass a double-clicked file on the command line
 * but sends an event, to the app being started as well as to one already running, so the handler is
 * installed first thing and keeps what arrives until the app is ready for it.
 */
final class OpenFileEvents {

    private static final Logger logger = LoggerFactory.getLogger(OpenFileEvents.class);

    private static final List<Path> buffered = new ArrayList<>();
    private static Consumer<List<Path>> consumer;

    private OpenFileEvents() {}

    /** Listens for the files macOS asks the app to open. Does nothing elsewhere. */
    static void install() {
        if (OperatingSystem.current() != OperatingSystem.MAC
                || GraphicsEnvironment.isHeadless()
                || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            return;
        }
        try {
            Desktop.getDesktop()
                    .setOpenFileHandler(
                            event -> receive(event.getFiles().stream().map(File::toPath).toList()));
        } catch (RuntimeException e) {
            logger.warn("Cannot receive the files macOS asks to open.", e);
        }
    }

    static synchronized void receive(List<Path> files) {
        if (consumer == null) {
            buffered.addAll(files);
        } else {
            consumer.accept(files);
        }
    }

    /**
     * Returns the files that arrived so far and hands every later one to {@code next}.
     *
     * @param next takes the files that arrive from now on
     * @return the files that arrived before
     */
    static synchronized List<Path> takeAndForward(Consumer<List<Path>> next) {
        var arrived = List.copyOf(buffered);
        buffered.clear();
        consumer = next;
        return arrived;
    }

    /** For tests: back to buffering. */
    static synchronized void reset() {
        buffered.clear();
        consumer = null;
    }
}
