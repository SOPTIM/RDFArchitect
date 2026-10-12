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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.security.DatasetAccessDeniedException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class DatasetFilter implements Filter {

    /**
     * Every path this filter guards starts here. Derived rather than repeated, so that renaming the
     * route cannot leave the offset below pointing into the middle of a workspace name — a mistake
     * that builds cleanly and silently guards the wrong thing.
     */
    private static final String PREFIX = "/api/datasets/";

    private final DatabasePort databasePort;

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest httpRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        var requestMethod = httpRequest.getMethod();
        var requestURI = httpRequest.getRequestURI();

        // calls to OPTIONS are rejected as they come from preflight and are sent under a different
        // sessionID
        if (!requestMethod.equals("OPTIONS") && requestURI.startsWith(PREFIX)) {
            var datasetName = extractDatasetNameFromURI(httpRequest.getRequestURI());
            if (!hasDatasetAccess(requestMethod, requestURI, datasetName)) {
                throw new DatasetAccessDeniedException(SessionContext.getSessionId(), datasetName);
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean hasDatasetAccess(String method, String uri, String datasetName) {
        // Creating a workspace, and the two uploads that bring the workspace they address into
        // existence, are the only requests allowed to name one that does not exist yet. An import's
        // progress also stays readable when it failed before the workspace came into existence.
        if ("PUT".equals(method)
                && (uri.matches(PREFIX + "[^/]+")
                        || uri.matches(PREFIX + "[^/]+/graphs/[^/]+/content"))) {
            return true;
        }
        if (uri.matches(PREFIX + "[^/]+/graphs/content/imports(/.*)?")) {
            return true;
        }
        var datasets = databasePort.listDatasets();
        return datasets != null && datasets.contains(datasetName);
    }

    private String extractDatasetNameFromURI(String uri) {
        if (uri != null && uri.startsWith(PREFIX)) {
            String remaining = uri.substring(PREFIX.length());
            int slashIndex = remaining.indexOf('/');
            String encoded = slashIndex == -1 ? remaining : remaining.substring(0, slashIndex);
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }
        return null;
    }
}
