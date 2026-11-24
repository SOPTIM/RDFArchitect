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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A server that answers like the desktop app, with a field in its answers that this version does
 * not know.
 */
final class DesktopAppStub implements AutoCloseable {

    static final String VERSION = "1.9.0";

    private final HttpServer server;
    private final List<String> tokensReceived = new CopyOnWriteArrayList<>();

    /**
     * @param holding the token it proves to hold and asks for, {@code null} for a program that has
     *     none
     */
    DesktopAppStub(DesktopAccessToken holding) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/desktop", exchange -> answer(exchange, holding));
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    List<String> tokensReceived() {
        return tokensReceived;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange, DesktopAccessToken holding) throws IOException {
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
            send(exchange, 401, "");
        } else if ("POST".equals(exchange.getRequestMethod())) {
            send(exchange, 201, "{\"id\":\"id-1\",\"unknownField\":true}");
        } else {
            send(
                    exchange,
                    200,
                    "{\"application\":\"RDFArchitect\",\"version\":\""
                            + VERSION
                            + "\",\"unknownField\":true}");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
