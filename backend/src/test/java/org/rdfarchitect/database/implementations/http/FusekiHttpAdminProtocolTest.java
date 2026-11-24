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

package org.rdfarchitect.database.implementations.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.database.DataAccessException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

class FusekiHttpAdminProtocolTest {

    private HttpServer server;

    /** A Fuseki that answers {@code GET /$/datasets} with the given body. */
    private FusekiHttpAdminProtocol fusekiAnswering(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/$/datasets",
                exchange -> {
                    var bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        return new FusekiHttpAdminProtocol("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listDatasets_datasetsPresent_returnsTheirNames() throws IOException {
        var fuseki =
                fusekiAnswering(
                        """
                        {"datasets": [
                          {"ds.name": "/default", "ds.state": true},
                          {"ds.name": "/snapshot-abc", "ds.state": true}
                        ]}
                        """);

        assertThat(fuseki.listDatasets()).containsExactly("default", "snapshot-abc");
    }

    @Test
    void listDatasets_emptyFuseki_returnsEmptyList() throws IOException {
        assertThat(fusekiAnswering("{\"datasets\": []}").listDatasets()).isEmpty();
    }

    @Test
    void listDatasets_unreadableAnswer_throwsDataAccessException() throws IOException {
        assertThatThrownBy(() -> fusekiAnswering("not json").listDatasets())
                .isInstanceOf(DataAccessException.class);
        tearDown();
        assertThatThrownBy(() -> fusekiAnswering("{\"other\": 1}").listDatasets())
                .isInstanceOf(DataAccessException.class);
        tearDown();
        assertThatThrownBy(() -> fusekiAnswering("{\"datasets\": [{}]}").listDatasets())
                .isInstanceOf(DataAccessException.class);
    }
}
