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

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ApplicationContextException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class DesktopLauncherTest {

    @Test
    void namesTheSessionCookieAfterThePort() {
        var options = DesktopOptions.parse(new String[] {"--desktop.close-grace=30s"}, Map.of());

        assertThat(DesktopLauncher.springArguments(options, 17407, Path.of("/data/rdfa")))
                .containsSubsequence(
                        "--server.port=17407",
                        "--server.servlet.session.cookie.name=RDFA_DESKTOP_SESSION_17407")
                .contains("--desktop.data-directory=" + Path.of("/data/rdfa"))
                .endsWith("--desktop.close-grace=30s");
    }

    @Test
    void opensTheEditor() {
        assertThat(DesktopLauncher.startPage(17407)).isEqualTo("http://127.0.0.1:17407/mainpage");
        assertThat(DesktopLauncher.startPage(17407, List.of()))
                .isEqualTo("http://127.0.0.1:17407/mainpage");
        assertThat(DesktopLauncher.startPage(17407, List.of("a", "b")))
                .isEqualTo("http://127.0.0.1:17407/mainpage?open=a,b");
    }

    @Test
    void recognisesAPortTakenWhileStartingUp() {
        var wrapped =
                new ApplicationContextException(
                        "Failed to start bean 'webServerStartStop'", new PortInUseException(17407));

        assertThat(DesktopLauncher.isPortInUse(wrapped)).isTrue();
        assertThat(DesktopLauncher.isPortInUse(new IllegalStateException("other"))).isFalse();
    }
}
