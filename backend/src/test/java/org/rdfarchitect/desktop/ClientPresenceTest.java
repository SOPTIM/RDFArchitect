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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class ClientPresenceTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final AtomicInteger closings = new AtomicInteger();
    private ClientPresence presence;

    @BeforeEach
    void setUp() {
        var properties = new DesktopProperties();
        properties.setClientTimeout(Duration.ofSeconds(150));
        properties.setCloseGrace(Duration.ofSeconds(15));
        properties.setStartupTimeout(Duration.ofMinutes(5));
        presence = new ClientPresence(properties, now::get, closings::incrementAndGet);
    }

    /** Moves the clock in steps of one second, checking after each, as the scheduler does. */
    private void runFor(Duration duration) {
        for (long s = 0; s < duration.toSeconds(); s++) {
            now.addAndGet(Duration.ofSeconds(1).toNanos());
            presence.check();
        }
    }

    @Test
    void check_windowKeepsSendingHeartbeats_keepsAppRunning() {
        presence.heartbeat("a");
        for (int i = 0; i < 60; i++) {
            runFor(Duration.ofSeconds(10));
            presence.heartbeat("a");
        }

        assertThat(closings).hasValue(0);
        assertThat(presence.openWindows()).isEqualTo(1);
    }

    @Test
    void check_lastWindowSaidGoodbye_closesAfterGrace() {
        presence.heartbeat("a");
        presence.goodbye("a");

        runFor(Duration.ofSeconds(14));
        assertThat(closings).hasValue(0);

        runFor(Duration.ofSeconds(2));
        assertThat(closings).hasValue(1);
        assertThat(presence.isClosed()).isTrue();
    }

    @Test
    void check_reloadWithinGrace_keepsAppRunning() {
        presence.heartbeat("before-reload");
        presence.goodbye("before-reload");
        runFor(Duration.ofSeconds(3));
        presence.heartbeat("after-reload");

        runFor(Duration.ofSeconds(60));

        assertThat(closings).hasValue(0);
    }

    @Test
    void check_anotherWindowStillOpen_keepsAppRunning() {
        presence.heartbeat("a");
        presence.heartbeat("b");
        presence.goodbye("a");

        runFor(Duration.ofSeconds(30));
        presence.heartbeat("b");

        assertThat(closings).hasValue(0);
    }

    @Test
    void check_windowWentSilent_givesItUpAfterTimeout() {
        presence.heartbeat("crashed");

        runFor(Duration.ofSeconds(150));
        assertThat(closings).hasValue(0);

        runFor(Duration.ofSeconds(2));
        assertThat(closings).hasValue(1);
    }

    @Test
    void check_machineWasSuspended_doesNotCountAsSilence() {
        presence.heartbeat("a");
        runFor(Duration.ofSeconds(5));

        // The machine sleeps for an hour; no check runs meanwhile.
        now.addAndGet(Duration.ofHours(1).toNanos());
        presence.check();
        assertThat(closings).hasValue(0);

        // The window's next heartbeat arrives within its timeout after waking up.
        runFor(Duration.ofSeconds(10));
        presence.heartbeat("a");
        runFor(Duration.ofSeconds(140));
        assertThat(closings).hasValue(0);
    }

    @Test
    void check_noWindowEverConnects_givesUpAfterStartupTimeout() {
        runFor(Duration.ofMinutes(5));
        assertThat(closings).hasValue(0);

        runFor(Duration.ofSeconds(2));
        assertThat(closings).hasValue(1);
    }

    @Test
    void check_windowConnectsAfterClose_closesOnlyOnce() {
        presence.heartbeat("a");
        presence.goodbye("a");
        runFor(Duration.ofSeconds(40));
        presence.heartbeat("late");
        presence.goodbye("late");
        runFor(Duration.ofSeconds(40));

        assertThat(closings).hasValue(1);
    }
}
