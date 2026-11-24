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

package org.rdfarchitect.api.controller.datasets.graphs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A graph name travels as a path segment of almost every route, so every character a name may hold
 * has to arrive at the controller as it was sent, through the servlet container and not just
 * through Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchemaNameRoutingTest {

    @LocalServerPort private int port;

    private HttpClient client;
    private String workspace;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        workspace = "ws-" + UUID.randomUUID();
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-profile", "my profile", "100%", "a#b", "a&b", "a+b", "Équipement"})
    void schemaName_withCharactersAUrlEncodes_arrivesUnchanged(String name)
            throws IOException, InterruptedException {
        var created = send("PUT", graphPath(name) + "/content", "*/*");
        assertThat(created.statusCode()).isEqualTo(200);

        var graphs =
                send(
                        "GET",
                        "/api/workspaces/" + encode(workspace) + "/schemas",
                        "application/json");
        assertThat(graphs.body()).contains("\"name\":\"" + name + "\"");

        var exported = send("GET", graphPath(name) + "/content", "text/turtle");
        assertThat(exported.statusCode()).isEqualTo(200);
    }

    /** Tomcat drops a header that holds a character outside Latin-1 instead of sending it. */
    @ParameterizedTest
    @ValueSource(strings = {"中文", "Équipement", "my profile"})
    void export_schemaNameOutsideAscii_keepsItsFileName(String name)
            throws IOException, InterruptedException {
        send("PUT", graphPath(name) + "/content", "*/*");

        var exported = send("GET", graphPath(name) + "/content", "text/turtle");

        var header = exported.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).isPresent();
        assertThat(ContentDisposition.parse(header.get()).getFilename()).isEqualTo(name + ".ttl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a:b", "a;b", "a|b", "CON"})
    void schemaName_invalid_isRejectedWithBadRequest(String name)
            throws IOException, InterruptedException {
        var response = send("PUT", graphPath(name) + "/content", "*/*");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private String graphPath(String name) {
        return "/api/workspaces/" + encode(workspace) + "/schemas/" + encode(name);
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> send(String method, String path, String accept)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", accept)
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
