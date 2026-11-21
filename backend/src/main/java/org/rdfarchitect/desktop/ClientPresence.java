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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Keeps track of the browser windows showing the desktop app and tells when the last one has gone.
 *
 * <p>Each window sends a heartbeat under an id of its own and says goodbye when it unloads. A
 * window that stops sending without saying goodbye (a crash, a killed browser) is given up after
 * {@link DesktopProperties#getClientTimeout()}. Once no window is left the backend is not needed
 * any more, but it waits {@link DesktopProperties#getCloseGrace()} after a goodbye, since a reload
 * says goodbye too and is back a moment later.
 *
 * <p>A heartbeat cannot arrive while the computer sleeps, so waking up would otherwise look like
 * every window having gone silent at once. A check that runs much later than scheduled is therefore
 * taken for a suspend, and every window is given a fresh timeout instead.
 *
 * <p>{@link #check()} is called periodically by the owner; this class keeps no thread of its own.
 */
public class ClientPresence {

    private static final Logger logger = LoggerFactory.getLogger(ClientPresence.class);

    /** A gap between two checks beyond this is taken for a suspend of the machine or the JVM. */
    private static final Duration SUSPEND_GAP = Duration.ofSeconds(30);

    private final DesktopProperties properties;
    private final LongSupplier nanoClock;
    private final Runnable onLastWindowClosed;

    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long startedAt;

    private volatile boolean anyWindowConnected;
    private volatile long lastGoodbyeAt;
    private volatile boolean goodbyeSeen;
    private long lastCheckAt;

    /**
     * @param properties the timings to apply
     * @param nanoClock a monotonic clock in nanoseconds, {@link System#nanoTime()} outside tests
     * @param onLastWindowClosed run once, from the thread calling {@link #check()}, when no window
     *     is left
     */
    public ClientPresence(
            DesktopProperties properties, LongSupplier nanoClock, Runnable onLastWindowClosed) {
        this.properties = properties;
        this.nanoClock = nanoClock;
        this.onLastWindowClosed = onLastWindowClosed;
        this.startedAt = nanoClock.getAsLong();
        this.lastCheckAt = startedAt;
    }

    /** Records that the window is still open. */
    public void heartbeat(String clientId) {
        if (lastSeen.put(clientId, nanoClock.getAsLong()) == null) {
            logger.info("Window {} connected.", clientId);
        }
        anyWindowConnected = true;
    }

    /** Records that the window has closed or is reloading. */
    public void goodbye(String clientId) {
        if (lastSeen.remove(clientId) != null) {
            logger.info("Window {} said goodbye.", clientId);
        }
        lastGoodbyeAt = nanoClock.getAsLong();
        goodbyeSeen = true;
    }

    /** Returns how many windows count as open. */
    public int openWindows() {
        return lastSeen.size();
    }

    /** Returns whether the last window has been found closed and the callback has run. */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Gives up windows that went silent and runs the callback if none is left. Safe to call from a
     * single scheduler thread only.
     */
    public synchronized void check() {
        if (closed.get()) {
            return;
        }
        var now = nanoClock.getAsLong();
        var sinceLastCheck = now - lastCheckAt;
        lastCheckAt = now;
        if (sinceLastCheck > SUSPEND_GAP.toNanos()) {
            logger.info(
                    "No check ran for {} s, assuming the computer was asleep.",
                    Duration.ofNanos(sinceLastCheck).toSeconds());
            lastSeen.replaceAll((_, _) -> now);
            if (goodbyeSeen) {
                lastGoodbyeAt = now;
            }
            return;
        }

        var timeout = properties.getClientTimeout().toNanos();
        lastSeen.entrySet()
                .removeIf(
                        entry -> {
                            var silent = now - entry.getValue() > timeout;
                            if (silent) {
                                logger.info("Window {} went silent.", entry.getKey());
                            }
                            return silent;
                        });
        if (!lastSeen.isEmpty()) {
            return;
        }

        if (!anyWindowConnected) {
            if (now - startedAt > properties.getStartupTimeout().toNanos()) {
                close("No window connected since the start.");
            }
            return;
        }
        if (goodbyeSeen && now - lastGoodbyeAt < properties.getCloseGrace().toNanos()) {
            return;
        }
        close("The last window has closed.");
    }

    private void close(String reason) {
        if (closed.compareAndSet(false, true)) {
            logger.info("{} Shutting down.", reason);
            onLastWindowClosed.run();
        }
    }
}
