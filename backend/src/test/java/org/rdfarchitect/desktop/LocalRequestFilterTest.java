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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalRequestFilterTest {

    private static final int PORT = 17407;

    @ParameterizedTest
    @CsvSource({
        "127.0.0.1:17407, true",
        "localhost:17407, true",
        "LOCALHOST:17407, true",
        "[::1]:17407, true",
        "127.0.0.1:8080, false",
        "127.0.0.1, false",
        "rebound.example:17407, false",
        "127.0.0.1.rebound.example:17407, false",
    })
    void isLoopbackAuthority_host_acceptsOnlyItsOwnLoopbackAddress(String host, boolean accepted) {
        assertThat(LocalRequestFilter.isLoopbackAuthority(host, PORT)).isEqualTo(accepted);
    }

    @ParameterizedTest
    @CsvSource({
        "http://127.0.0.1:17407, true",
        "http://localhost:17407, true",
        "https://127.0.0.1:17407, false",
        "http://127.0.0.1:1407, false",
        "http://evil.example, false",
        "null, false",
    })
    void isLoopbackOrigin_origin_acceptsOnlyItsOwn(String origin, boolean accepted) {
        assertThat(LocalRequestFilter.isLoopbackOrigin(origin, PORT)).isEqualTo(accepted);
    }

    @Test
    void doFilter_anotherHost_isRefused() throws Exception {
        var response = filter(request("GET", "rebound.example:" + PORT, null));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void doFilter_changeFromAnotherOrigin_isRefused() throws Exception {
        var response = filter(request("POST", "127.0.0.1:" + PORT, "http://evil.example"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void doFilter_readFromAnotherOrigin_passes() throws Exception {
        // Reading is up to the browser: without CORS headers it does not hand the answer over.
        var response = filter(request("GET", "127.0.0.1:" + PORT, "http://evil.example"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_ownOrigin_passes() throws Exception {
        var response = filter(request("PUT", "127.0.0.1:" + PORT, "http://127.0.0.1:" + PORT));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_noOrigin_passes() throws Exception {
        // Tools such as curl send none, and a browser always sends one with a cross-origin change.
        var response = filter(request("DELETE", "localhost:" + PORT, null));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String method, String host, String origin) {
        var request = new MockHttpServletRequest(method, "/api/workspaces/x");
        request.setLocalPort(PORT);
        request.addHeader("Host", host);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    private static MockHttpServletResponse filter(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        new LocalRequestFilter().doFilter(request, response, new MockFilterChain());
        return response;
    }
}
