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

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DesktopAccessFilterTest {

    private static final int PORT = 17407;
    private static final String COOKIE = "RDFA_DESKTOP_ACCESS_17407";

    private final DesktopAccessToken token = DesktopAccessToken.generate();
    private final DesktopAccessFilter filter = new DesktopAccessFilter(token);

    @Test
    void doFilter_apiRequestWithoutToken_answersUnauthorized() throws Exception {
        var chain = new MockFilterChain();

        var response = filter(request("GET", "/api/desktop/recovery"), chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_apiRequestWithTokenHeader_passes() throws Exception {
        var request = request("POST", "/api/desktop/open-requests");
        request.addHeader(DesktopAccessFilter.TOKEN_HEADER, token.value());

        assertThat(filter(request).getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_apiRequestWithTokenCookie_passes() throws Exception {
        var request = request("GET", "/api/workspaces/x/export");
        request.setCookies(new Cookie(COOKIE, token.value()));

        assertThat(filter(request).getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_apiRequestWithWrongToken_answersUnauthorized() throws Exception {
        var withHeader = request("GET", "/api");
        withHeader.addHeader(DesktopAccessFilter.TOKEN_HEADER, "wrong");
        var withCookie = request("GET", "/api/desktop");
        withCookie.setCookies(new Cookie(COOKIE, "wrong"));

        assertThat(filter(withHeader).getStatus()).isEqualTo(401);
        assertThat(filter(withCookie).getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_tokenCookieOfAnotherPort_answersUnauthorized() throws Exception {
        var request = request("GET", "/api/desktop");
        request.setCookies(new Cookie("RDFA_DESKTOP_ACCESS_1407", token.value()));

        assertThat(filter(request).getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_apiNamedByTheRequestUriOrTheNormalisedPath_answersUnauthorized()
            throws Exception {
        var encoded = request("GET", "/api/desktop/recovery");
        encoded.setRequestURI("/%61pi/desktop/recovery");
        var dotted = request("GET", "/api/desktop/recovery");
        dotted.setRequestURI("/ui/../api/desktop/recovery");
        var leaving = request("GET", "/mainpage");
        leaving.setRequestURI("/api/../mainpage");

        assertThat(filter(encoded).getStatus()).isEqualTo(401);
        assertThat(filter(dotted).getStatus()).isEqualTo(401);
        assertThat(filter(leaving).getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/mainpage", "/env.js", "/_app/immutable/start.js", "/apidocs"})
    void doFilter_userInterfaceWithoutToken_passes(String path) throws Exception {
        assertThat(filter(request("GET", path)).getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_session_setsTheCookieAndRedirects() throws Exception {
        var request = request("GET", DesktopAccessFilter.SESSION_PATH);
        request.setParameter("token", token.value());
        request.setParameter("next", "/mainpage?open=a,b");
        var chain = new MockFilterChain();

        var response = filter(request, chain);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/mainpage?open=a,b");
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith(COOKIE + "=" + token.value() + ";")
                .contains("Path=/", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Max-Age", "Expires");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"wrong", ""})
    void doFilter_sessionWithWrongToken_answersUnauthorized(String candidate) throws Exception {
        var request = request("GET", DesktopAccessFilter.SESSION_PATH);
        if (candidate != null) {
            request.setParameter("token", candidate);
        }

        var response = filter(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "//evil.example/",
                "/\\evil.example/",
                "https://evil.example/",
                "javascript:alert(1)",
                "mainpage",
                "/\t/evil.example/",
                "/main\npage",
                ""
            })
    void doFilter_sessionWithForeignNext_redirectsToTheStartPage(String next) throws Exception {
        var request = request("GET", DesktopAccessFilter.SESSION_PATH);
        request.setParameter("token", token.value());
        request.setParameter("next", next);

        var response = filter(request);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/mainpage");
    }

    @Test
    void doFilter_sessionWithoutNext_redirectsToTheStartPage() throws Exception {
        var request = request("GET", DesktopAccessFilter.SESSION_PATH);
        request.setParameter("token", token.value());

        assertThat(filter(request).getHeader("Location")).isEqualTo("/mainpage");
    }

    @Test
    void doFilter_challengeWithoutToken_answersWithProof() throws Exception {
        var request = request("GET", "/api/desktop");
        request.addHeader(DesktopAccessFilter.CHALLENGE_HEADER, "nonce");

        var response = filter(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(token.isProof("nonce", response.getHeader(DesktopAccessFilter.PROOF_HEADER)))
                .isTrue();
    }

    @Test
    void doFilter_challengeWithToken_answersWithProof() throws Exception {
        var request = request("GET", "/api/desktop");
        request.addHeader(DesktopAccessFilter.TOKEN_HEADER, token.value());
        request.addHeader(DesktopAccessFilter.CHALLENGE_HEADER, "nonce");

        var response = filter(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(DesktopAccessFilter.PROOF_HEADER))
                .isEqualTo(token.proof("nonce"));
    }

    @Test
    void doFilter_noChallenge_answersWithoutProof() throws Exception {
        assertThat(
                        filter(request("GET", "/api/desktop"))
                                .getHeader(DesktopAccessFilter.PROOF_HEADER))
                .isNull();
    }

    /** A request as the servlet container hands it over, with the path already normalised. */
    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setLocalPort(PORT);
        return request;
    }

    private MockHttpServletResponse filter(MockHttpServletRequest request) throws Exception {
        return filter(request, new MockFilterChain());
    }

    private MockHttpServletResponse filter(MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
