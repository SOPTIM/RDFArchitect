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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

/** The desktop profile as the browser window sees it, over HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "desktop.exit-when-closed=false",
            "desktop.user-interface=classpath:/desktop-ui/",
            "desktop.data-directory=${java.io.tmpdir}/rdfarchitect-desktop-test-${random.uuid}"
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
                .contains("\"unsavedWorkspaces\":[]");

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

    private HttpResponse<String> postJson(String path, String json)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void opensAFileTheOperatingSystemHandsOverAndSavesBackToIt(@TempDir Path directory)
            throws Exception {
        assertThat(send("PUT", "/api/datasets/Handed", null).statusCode()).isEqualTo(200);
        var file =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(url("/api/desktop/workspaces/Handed/file")))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        var onDisk = Files.write(directory.resolve("Handed.rdfa"), file.body());

        var registered =
                postJson(
                        "/api/desktop/open-requests",
                        "{\"path\":"
                                + new ObjectMapper().writeValueAsString(onDisk.toString())
                                + "}");
        assertThat(registered.statusCode()).isEqualTo(201);
        var id = new ObjectMapper().readTree(registered.body()).get("id").asText();
        assertThat(get("/api/desktop/open-requests/" + id).statusCode()).isEqualTo(200);

        assertThat(
                        send("PUT", "/api/desktop/workspaces/Handed/opened-from/" + id, null)
                                .statusCode())
                .isEqualTo(204);
        assertThat(get("/api/desktop/open-requests/" + id).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/desktop/windows/w/heartbeat", null).body())
                .contains("\"workspacesWithFile\":[\"Handed\"]");

        Files.writeString(onDisk, "overwritten");
        assertThat(send("POST", "/api/desktop/workspaces/Handed/file", null).statusCode())
                .isEqualTo(204);
        assertThat(Files.size(onDisk)).isGreaterThan(100);

        // Saved elsewhere by the browser: that file is the workspace's file from now on.
        assertThat(send("PUT", "/api/desktop/workspaces/Handed/saved", null).statusCode())
                .isEqualTo(204);
        assertThat(send("POST", "/api/desktop/workspaces/Handed/file", null).statusCode())
                .isEqualTo(409);
    }

    @Test
    void refusesToOpenWhatIsNoWorkspaceFile(@TempDir Path directory) throws Exception {
        var text = Files.writeString(directory.resolve("notes.txt"), "text");

        assertThat(
                        postJson(
                                        "/api/desktop/open-requests",
                                        "{\"path\":\"" + text.getFileName() + "\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(postJson("/api/desktop/open-requests", "{}").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/desktop/workspaces/missing/file", null).statusCode())
                .isEqualTo(409);
    }

    @Test
    void offersNoWorkspacesFromAPreviousRunThatKeptNone() throws Exception {
        var listed = get("/api/desktop/recovery");

        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).isEqualTo("[]");
        assertThat(get("/api/desktop/recovery/missing.rdfa").statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/desktop/recovery/missing.rdfa", null).statusCode())
                .isEqualTo(404);
    }

    @Test
    void answersNotFoundForAWorkspaceThatDoesNotExist() throws Exception {
        assertThat(get("/api/desktop/workspaces/missing/file").statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/api/desktop/workspaces/missing/saved", null).statusCode())
                .isEqualTo(404);
    }
}
