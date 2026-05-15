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

package org.rdfarchitect.filters;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.context.UserSettingsContext;

import java.io.IOException;

public class SessionContextFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest httpRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        try {
            if (!"OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                SessionContext.setSessionId(httpRequest.getSession().getId());
            }
            var usePackagePrefix = true;
            var normalizeComments = true;
            if (httpRequest.getCookies() != null) {
                for (Cookie cookie : httpRequest.getCookies()) {
                    if ("RDFA_USER_SETTINGS".equals(cookie.getName())) {
                        try {
                            var decoded =
                                    java.net.URLDecoder.decode(
                                            cookie.getValue(),
                                            java.nio.charset.StandardCharsets.UTF_8);
                            var node =
                                    new com.fasterxml.jackson.databind.ObjectMapper()
                                            .readTree(decoded);
                            usePackagePrefix = parseUsePackagePrefix(node);
                            normalizeComments = parseNormalizeComments(node);
                        } catch (Exception ignored) {
                        }
                        break;
                    }
                }
            }
            UserSettingsContext.set(
                    new UserSettingsContext.UserSettings(usePackagePrefix, normalizeComments));
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            SessionContext.clear();
            UserSettingsContext.clear();
        }
    }

    private boolean parseUsePackagePrefix(JsonNode node) {
        try {
            return node.path("usePackagePrefix").asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean parseNormalizeComments(JsonNode node) {
        try {
            return node.path("normalizeComments").asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }
}
