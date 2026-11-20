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

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built user interface, a single-page app: a path that names no file is one of its
 * routes, such as {@code /mainpage}, and is answered with {@code index.html}, which routes in the
 * browser. The same fallback the nginx image of the frontend uses ({@code try_files $uri
 * /index.html}).
 *
 * <p>Paths that look like a file or belong to the backend are not routes, so a missing script or an
 * unknown API path is a 404 rather than a page of HTML the caller cannot use.
 */
class UserInterfaceResolver extends PathResourceResolver {

    static final String INDEX = "index.html";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        var resource = super.getResource(resourcePath, location);
        if (resource != null || !isRoute(resourcePath)) {
            return resource;
        }
        return super.getResource(INDEX, location);
    }

    static boolean isRoute(String resourcePath) {
        if (resourcePath.startsWith("api/")
                || resourcePath.equals("api")
                || resourcePath.startsWith("_app/")
                || resourcePath.startsWith("v3/")) {
            return false;
        }
        var lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        return !lastSegment.contains(".");
    }
}
