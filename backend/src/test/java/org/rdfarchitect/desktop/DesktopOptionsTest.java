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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

class DesktopOptionsTest {

    @Test
    void parse_noArguments_defaultsToAppWindowOnFixedPort() {
        var options = DesktopOptions.parse(new String[0], Map.of());

        assertThat(options.port()).isEqualTo(DesktopOptions.DEFAULT_PORT);
        assertThat(options.browser()).isEqualTo(DesktopOptions.BrowserMode.APP);
        assertThat(options.dataDirectory()).isNull();
        assertThat(options.help()).isFalse();
        assertThat(options.springArguments()).isEmpty();
        assertThat(options.files()).isEmpty();
    }

    @Test
    void parse_mixedArguments_readsOwnOptionsAndPassesRestToSpring() {
        var options =
                DesktopOptions.parse(
                        new String[] {
                            "--port=0",
                            "--browser=none",
                            "--data-dir=/tmp/rdfa",
                            "--desktop.exit-when-closed=false",
                            "draft.rdfa"
                        },
                        Map.of());

        assertThat(options.port()).isZero();
        assertThat(options.browser()).isEqualTo(DesktopOptions.BrowserMode.NONE);
        assertThat(options.dataDirectory()).isEqualTo(Path.of("/tmp/rdfa"));
        assertThat(options.springArguments()).containsExactly("--desktop.exit-when-closed=false");
        assertThat(options.files()).containsExactly(Path.of("draft.rdfa"));
    }

    @Test
    void parse_optionInArgumentsAndEnvironment_commandLineWins() {
        var env =
                Map.of(
                        "RDFARCHITECT_PORT", "9000",
                        "RDFARCHITECT_BROWSER", "default",
                        "RDFARCHITECT_DATA_DIR", "/from/env");

        var fromEnv = DesktopOptions.parse(new String[0], env);
        var fromArgs = DesktopOptions.parse(new String[] {"--port=9001", "--browser=app"}, env);

        assertThat(fromEnv.port()).isEqualTo(9000);
        assertThat(fromEnv.browser()).isEqualTo(DesktopOptions.BrowserMode.DEFAULT);
        assertThat(fromEnv.dataDirectory()).isEqualTo(Path.of("/from/env"));
        assertThat(fromArgs.port()).isEqualTo(9001);
        assertThat(fromArgs.browser()).isEqualTo(DesktopOptions.BrowserMode.APP);
    }

    @Test
    void parse_pathsAndFileUris_takesBothAsFiles() {
        var options =
                DesktopOptions.parse(
                        new String[] {"/tmp/a.rdfa", "file:///tmp/My%20Draft.rdfa"}, Map.of());

        assertThat(options.files())
                .containsExactly(Path.of("/tmp/a.rdfa"), Path.of("/tmp/My Draft.rdfa"));
    }

    @Test
    void parse_processSerialNumberFromFinder_ignoresIt() {
        var options = DesktopOptions.parse(new String[] {"-psn_0_123456"}, Map.of());

        assertThat(options.files()).isEmpty();
    }

    @Test
    void parse_unreadableArgument_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> DesktopOptions.parse(new String[] {"--port=x"}, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DesktopOptions.parse(new String[] {"--port=70000"}, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DesktopOptions.parse(new String[] {"--browser=lynx"}, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DesktopOptions.parse(new String[] {"-x"}, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_helpOption_setsHelp() {
        assertThat(DesktopOptions.parse(new String[] {"--help"}, Map.of()).help()).isTrue();
        assertThat(DesktopOptions.USAGE).contains("--browser=<mode>");
    }
}
