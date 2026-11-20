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
import org.rdfarchitect.api.controller.desktop.DesktopRESTController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.ZipInputStream;

/** The desktop profile as the browser window sees it, over HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "desktop.exit-when-closed=false",
            "desktop.user-interface=classpath:/desktop-ui/"
        })
@ActiveProfiles("desktop")
class DesktopApplicationTest {

    @LocalServerPort private int port;

    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String origin)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(URI.create(url(path)))
                        .method(method, HttpRequest.BodyPublishers.noBody());
        if (origin != null) {
            request.header("Origin", origin);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesTheUserInterfaceForEveryRoute() throws Exception {
        for (var route : new String[] {"/", "/mainpage", "/shacl/workbench"}) {
            var response = get(route);

            assertThat(response.statusCode()).as(route).isEqualTo(200);
            assertThat(response.body()).as(route).contains("fixture");
            assertThat(response.headers().firstValue("Cache-Control"))
                    .as(route)
                    .hasValue("no-cache");
        }
    }

    @Test
    void answersMissingFilesAndUnknownApiPathsWithNotFound() throws Exception {
        assertThat(get("/_app/immutable/missing.js").statusCode()).isEqualTo(404);
        assertThat(get("/missing.png").statusCode()).isEqualTo(404);
        assertThat(get("/api/missing").statusCode()).isEqualTo(404);
    }

    @Test
    void letsTheBrowserKeepVersionedFilesForGood() throws Exception {
        var response = get("/_app/immutable/start.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow())
                .contains("immutable");
    }

    @Test
    void configuresTheUserInterfaceForThisBackend() throws Exception {
        var response = get("/env.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("text/javascript");
        assertThat(response.body())
                .contains("PUBLIC_BACKEND_URL: \"\"")
                .contains("PUBLIC_DESKTOP: \"true\"")
                .contains("PUBLIC_SHARE_SNAPSHOT_ENABLED: \"false\"")
                .contains("PUBLIC_DESKTOP_HEARTBEAT_SECONDS: \"10\"");
    }

    @Test
    void hasNoShareSnapshot() throws Exception {
        assertThat(send("POST", "/api/snapshots", null).statusCode()).isEqualTo(404);
        assertThat(get("/api/snapshots/token").statusCode()).isEqualTo(404);
    }

    @Test
    void refusesChangesRequestedByOtherPages() throws Exception {
        var foreign = send("POST", "/api/desktop/windows/w/heartbeat", "http://evil.example");
        var own = send("POST", "/api/desktop/windows/w/heartbeat", url(""));

        assertThat(foreign.statusCode()).isEqualTo(403);
        assertThat(own.statusCode()).isEqualTo(200);
    }

    @Test
    void identifiesItself() throws Exception {
        var response = get("/api/desktop");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"application\":\"RDFArchitect\"");
    }

    @Test
    void savesAWorkspaceToAFileNamingItsVersion() throws Exception {
        assertThat(send("PUT", "/api/datasets/Draft%20One", null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/desktop/windows/w/heartbeat", null).body())
                .isEqualTo("{\"unsavedWorkspaces\":[]}");

        var file =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(url("/api/desktop/workspaces/Draft%20One/file")))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());

        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.headers().firstValue(DesktopRESTController.WORKSPACE_VERSION_HEADER))
                .isPresent();
        assertThat(file.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("Draft One.rdfa");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(file.body()))) {
            assertThat(zip.getNextEntry()).isNotNull();
        }
    }

    @Test
    void answersNotFoundForAWorkspaceThatDoesNotExist() throws Exception {
        assertThat(get("/api/desktop/workspaces/missing/file").statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/api/desktop/workspaces/missing/saved", null).statusCode())
                .isEqualTo(404);
    }
}
