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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Lets only the user who started the desktop app use its API.
 *
 * <p>Every user of the computer can reach the loopback interface the backend listens on. So every
 * request under {@code /api/} has to carry the run's {@link DesktopAccessToken}, or is answered
 * with 401:
 *
 * <ul>
 *   <li>The browser window sends it as cookie. {@code GET /desktop/session?token=…&next=…} sets the
 *       cookie and redirects to {@code next}, a page of the app. The launcher's pages in the user's
 *       data directory forward the window there (see {@link LaunchPages}).
 *   <li>The launcher sends it in the {@value #TOKEN_HEADER} header.
 * </ul>
 *
 * <p>The user interface and {@code /env.js} hold nothing private and need no token.
 *
 * <p>A request with a {@value #CHALLENGE_HEADER} header gets a {@value #PROOF_HEADER} header with
 * the {@linkplain DesktopAccessToken#proof proof} in its answer, with or without the token. A later
 * start of the app checks it before it trusts the backend with the token.
 */
public class DesktopAccessFilter extends OncePerRequestFilter {

    static final String SESSION_PATH = "/desktop/session";
    static final String START_PAGE = "/mainpage";
    static final String TOKEN_PARAMETER = "token";
    static final String NEXT_PARAMETER = "next";
    static final String TOKEN_HEADER = "X-RDFA-Desktop-Token";
    static final String CHALLENGE_HEADER = "X-RDFA-Desktop-Challenge";
    static final String PROOF_HEADER = "X-RDFA-Desktop-Proof";

    private static final String COOKIE_PREFIX = "RDFA_DESKTOP_ACCESS_";
    private static final String API_PREFIX = "/api";

    private static final Logger logger = LoggerFactory.getLogger(DesktopAccessFilter.class);

    private static final UrlPathHelper DECODING_PATH_HELPER = new UrlPathHelper();

    private final DesktopAccessToken token;

    public DesktopAccessFilter(DesktopAccessToken token) {
        this.token = token;
    }

    /**
     * Names the cookie after the port: browsers keep cookies per host, not per port, and another
     * backend on this computer must not overwrite it.
     */
    static String cookieName(int port) {
        return COOKIE_PREFIX + port;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var challenge = request.getHeader(CHALLENGE_HEADER);
        if (challenge != null) {
            response.setHeader(PROOF_HEADER, token.proof(challenge));
        }
        if (SESSION_PATH.equals(normalisedPath(request))) {
            startSession(request, response);
            return;
        }
        if (isApiRequest(request) && !carriesToken(request)) {
            logger.debug(
                    "Refused {} {} without access token.",
                    request.getMethod(),
                    request.getRequestURI());
            refuse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void startSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                || !token.matches(request.getParameter(TOKEN_PARAMETER))) {
            logger.warn("Refused to open a window with an unknown access token.");
            refuse(response);
            return;
        }
        var cookie =
                ResponseCookie.from(cookieName(request.getLocalPort()), token.value())
                        .httpOnly(true)
                        .sameSite("Strict")
                        .path("/")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader(HttpHeaders.LOCATION, localPathOr(request.getParameter(NEXT_PARAMETER)));
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
    }

    /**
     * Returns the page to go to, if it is a path of this app; anything else, such as {@code
     * //example.com}, would take the window to another site.
     */
    static String localPathOr(String next) {
        // Browsers read a backslash as a slash and drop tabs and line breaks from an address.
        if (next == null
                || !next.startsWith("/")
                || next.startsWith("//")
                || next.startsWith("/\\")) {
            return START_PAGE;
        }
        for (var i = 0; i < next.length(); i++) {
            var c = next.charAt(i);
            if (c <= ' ' || c >= 0x7f) {
                return START_PAGE;
            }
        }
        return next;
    }

    private boolean carriesToken(HttpServletRequest request) {
        if (token.matches(request.getHeader(TOKEN_HEADER))) {
            return true;
        }
        var cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        var name = cookieName(request.getLocalPort());
        for (var cookie : cookies) {
            if (name.equals(cookie.getName()) && token.matches(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the request is for the API. Checks the path as the servlet container normalised it as
     * well as the request URI decoded, which Spring MVC matches its controllers against.
     */
    private static boolean isApiRequest(HttpServletRequest request) {
        return isApiPath(normalisedPath(request))
                || isApiPath(DECODING_PATH_HELPER.getPathWithinApplication(request));
    }

    private static boolean isApiPath(String path) {
        return path.equals(API_PREFIX) || path.startsWith(API_PREFIX + "/");
    }

    private static String normalisedPath(HttpServletRequest request) {
        return request.getServletPath() + Objects.requireNonNullElse(request.getPathInfo(), "");
    }

    private static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter()
                .write(
                        "Missing or unknown access token. Start RDFArchitect to open a window"
                                + " of it.\n");
    }
}
