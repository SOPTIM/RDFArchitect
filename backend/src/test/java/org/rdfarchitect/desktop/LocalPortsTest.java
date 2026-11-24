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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class LocalPortsTest {

    private static final String INFO = "{\"application\":\"RDFArchitect\",\"version\":\"1.9.0\"}";

    private final DesktopAccessToken token = DesktopAccessToken.generate();
    private final List<String> tokensReceived = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void provenVersion_appHoldingTheToken_returnsItsVersion() throws Exception {
        var port = serve(token);

        assertThat(LocalPorts.provenVersion(port, token)).contains("1.9.0");
    }

    @Test
    void provenVersion_programAnsweringLikeTheAppWithoutProof_returnsNothingAndKeepsTheToken()
            throws Exception {
        var port = serve(null);

        assertThat(LocalPorts.provenVersion(port, token)).isEmpty();
        assertThat(tokensReceived).isEmpty();
    }

    @Test
    void provenVersion_appOfAnotherRun_returnsNothingAndKeepsTheToken() throws Exception {
        var port = serve(DesktopAccessToken.generate());

        assertThat(LocalPorts.provenVersion(port, token)).isEmpty();
        assertThat(tokensReceived).isEmpty();
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
        assertThat(tokensReceived).containsExactly(token.value());
    }

    /**
     * Starts a server that answers like the desktop app: with the proof for {@code holding}, if
     * given, and only to requests that carry that token.
     */
    private int serve(DesktopAccessToken holding) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(
                "/api/desktop",
                exchange -> {
                    var headers = exchange.getRequestHeaders();
                    var challenge = headers.getFirst(DesktopAccessFilter.CHALLENGE_HEADER);
                    if (challenge != null && holding != null) {
                        exchange.getResponseHeaders()
                                .add(DesktopAccessFilter.PROOF_HEADER, holding.proof(challenge));
                    }
                    var sent = headers.getFirst(DesktopAccessFilter.TOKEN_HEADER);
                    if (sent != null) {
                        tokensReceived.add(sent);
                    }
                    if (holding != null && !holding.matches(sent)) {
                        answer(exchange, 401, "");
                    } else if ("POST".equals(exchange.getRequestMethod())) {
                        answer(exchange, 201, "{\"id\":\"id-1\"}");
                    } else {
                        answer(exchange, 200, INFO);
                    }
                });
        server.start();
        return server.getAddress().getPort();
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
