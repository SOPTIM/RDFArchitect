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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Lets the desktop backend answer only the app's own pages.
 *
 * <p>The desktop backend listens on the loopback interface only, but every web page the user opens
 * in a browser can still send requests to it. Two checks keep those pages out:
 *
 * <ul>
 *   <li>The {@code Host} header has to name the loopback address the backend listens on. A page
 *       that points a host name of its own at {@code 127.0.0.1} (DNS rebinding) would otherwise be
 *       allowed to read every answer, since the browser takes it for the same origin.
 *   <li>A request that can change something must not come from another origin. The browser sends a
 *       form or a {@code fetch} from any page without asking, and only withholds the answer.
 * </ul>
 */
public class LocalRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LocalRequestFilter.class);

    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var port = request.getLocalPort();
        var host = request.getHeader(HttpHeaders.HOST);
        if (!isLoopbackAuthority(host, port)) {
            logger.warn("Refused a request for host \"{}\".", host);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unknown host");
            return;
        }
        var origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                && origin != null
                && !isLoopbackOrigin(origin, port)) {
            logger.warn("Refused a {} request from origin \"{}\".", request.getMethod(), origin);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Foreign origin");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isLoopbackAuthority(String authority, int port) {
        if (authority == null) {
            return false;
        }
        var lowered = authority.toLowerCase(Locale.ROOT);
        var suffix = ":" + port;
        if (!lowered.endsWith(suffix)) {
            return false;
        }
        return LOOPBACK_HOSTS.contains(lowered.substring(0, lowered.length() - suffix.length()));
    }

    static boolean isLoopbackOrigin(String origin, int port) {
        var prefix = "http://";
        return origin.regionMatches(true, 0, prefix, 0, prefix.length())
                && isLoopbackAuthority(origin.substring(prefix.length()), port);
    }
}
