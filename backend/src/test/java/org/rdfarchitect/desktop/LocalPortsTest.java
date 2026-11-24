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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class LocalPortsTest {

    private final DesktopAccessToken token = DesktopAccessToken.generate();
    private DesktopAppStub app;

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void provenVersion_appHoldingTheToken_returnsItsVersion() throws Exception {
        var port = serve(token);

        assertThat(LocalPorts.provenVersion(port, token)).contains(DesktopAppStub.VERSION);
    }

    @Test
    void provenVersion_programAnsweringLikeTheAppWithoutProof_returnsNothingAndKeepsTheToken()
            throws Exception {
        var port = serve(null);

        assertThat(LocalPorts.provenVersion(port, token)).isEmpty();
        assertThat(app.tokensReceived()).isEmpty();
    }

    @Test
    void provenVersion_appOfAnotherRun_returnsNothingAndKeepsTheToken() throws Exception {
        var port = serve(DesktopAccessToken.generate());

        assertThat(LocalPorts.provenVersion(port, token)).isEmpty();
        assertThat(app.tokensReceived()).isEmpty();
    }

    @Test
    void provenVersion_nothingListening_returnsNothing() throws Exception {
        assertThat(LocalPorts.provenVersion(LocalPorts.anyFreePort(), token)).isEmpty();
    }

    @Test
    void requestOpen_appHoldingTheToken_sendsTheTokenAndReturnsTheId(@TempDir Path directory)
            throws Exception {
        var port = serve(token);
        var file = Files.writeString(directory.resolve("Draft.rdfa"), "zip");

        assertThat(LocalPorts.requestOpen(port, token, file)).contains("id-1");
        assertThat(app.tokensReceived()).containsExactly(token.value());
    }

    private int serve(DesktopAccessToken holding) throws IOException {
        app = new DesktopAppStub(holding);
        return app.port();
    }
}
