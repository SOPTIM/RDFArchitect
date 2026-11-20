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

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the desktop app, the backend running on the user's own machine and serving the user
 * interface itself (profile {@code desktop}).
 *
 * <p>The app has no window of its own: the user interface runs in a browser window, which tells the
 * backend it is still open by sending a heartbeat. The timings below decide when the backend
 * concludes that every window is gone and shuts itself down.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "desktop")
public class DesktopProperties {

    /**
     * Where the built user interface is, a Spring resource location ending in {@code /}. The
     * desktop build puts it into the jar; {@code file:../frontend/build/} serves a local build.
     */
    private String userInterface = "classpath:/rdfarchitect-web/";

    /** Whether the backend shuts down once no window is open any more. */
    private boolean exitWhenClosed = true;

    /** How often a window sends its heartbeat. Handed to the user interface. */
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    /**
     * How long a window may stay silent before it counts as closed. Well above the heartbeat
     * interval, because browsers throttle the timers of a hidden window to one run a minute.
     */
    private Duration clientTimeout = Duration.ofSeconds(150);

    /**
     * How long the backend waits after the last window said goodbye. A reloaded window says goodbye
     * and comes back with a new heartbeat, which has to arrive within this time.
     */
    private Duration closeGrace = Duration.ofSeconds(15);

    /** How long the backend waits for the first window to connect before it gives up. */
    private Duration startupTimeout = Duration.ofMinutes(5);
}
