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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.rdfarchitect.api.dto.desktop.DesktopInfoDTO;
import org.rdfarchitect.api.dto.desktop.OpenRequestDTO;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Finds out what is listening on a port of the loopback interface. */
final class LocalPorts {

    static final String LOOPBACK = "127.0.0.1";

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private LocalPorts() {}

    static String baseUrl(int port) {
        return "http://" + LOOPBACK + ":" + port;
    }

    /** Whether nothing listens on the port yet. */
    static boolean isFree(int port) {
        try (var _ = new ServerSocket(port, 1, InetAddress.getByName(LOOPBACK))) {
            return true;
        } catch (IOException _) {
            return false;
        }
    }

    /** Returns a port nothing listens on. */
    static int anyFreePort() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            return socket.getLocalPort();
        }
    }

    /**
     * Asks the desktop app listening on the port to open a workspace file.
     *
     * @param port the port of the app
     * @param file the file to open
     * @return the id of the request, for the window to name
     */
    static Optional<String> requestOpen(int port, Path file) {
        var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        try {
            var mapper = new ObjectMapper();
            var body =
                    mapper.writeValueAsString(
                            new OpenRequestDTO(null, null, file.toAbsolutePath().toString()));
            var request =
                    HttpRequest.newBuilder(URI.create(baseUrl(port) + "/api/desktop/open-requests"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                return Optional.empty();
            }
            return Optional.ofNullable(
                    mapper.readValue(response.body(), OpenRequestDTO.class).getId());
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            client.close();
        }
    }

    /**
     * Returns the version of the desktop app listening on the port, if it is one.
     *
     * @param port the port to ask
     * @return its version, empty if something else listens there or nothing answers
     */
    static Optional<String> desktopAppVersion(int port) {
        var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        var request =
                HttpRequest.newBuilder(URI.create(baseUrl(port) + "/api/desktop"))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            var info = new ObjectMapper().readValue(response.body(), DesktopInfoDTO.class);
            return DesktopInfoDTO.APPLICATION.equals(info.getApplication())
                    ? Optional.ofNullable(info.getVersion())
                    : Optional.empty();
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            client.close();
        }
    }
}
