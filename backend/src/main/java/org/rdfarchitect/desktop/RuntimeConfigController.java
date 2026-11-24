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

import io.swagger.v3.oas.annotations.Hidden;

import org.rdfarchitect.config.AppVersionResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Writes the runtime configuration of the user interface, {@code /env.js}, for the desktop app.
 *
 * <p>The same file the nginx image of the frontend writes on start ({@code docker-entrypoint.sh}),
 * here derived from the backend's own settings so the two cannot disagree: the interface is served
 * from this backend, so it talks to it on its own origin, and it offers Share Snapshot only if the
 * backend does.
 */
@RestController
@Profile("desktop")
@Hidden
public class RuntimeConfigController {

    static final MediaType JAVASCRIPT = MediaType.parseMediaType("text/javascript;charset=UTF-8");

    private final String script;

    public RuntimeConfigController(
            AppVersionResolver versionResolver,
            DesktopProperties properties,
            @Value("${snapshots.enabled:true}") boolean snapshotsEnabled,
            @Value("${desktop.repository-url:https://github.com/SOPTIM/RDFArchitect}")
                    String repositoryUrl) {
        var config = new LinkedHashMap<String, String>();
        config.put("PUBLIC_BACKEND_URL", "");
        config.put("PUBLIC_APP_VERSION", versionResolver.resolveVersion());
        config.put("PUBLIC_COMMIT_SHA", commitOfBuild());
        config.put("PUBLIC_REPOSITORY_URL", repositoryUrl);
        config.put("PUBLIC_DEPLOYMENT_ENVIRONMENT", "Desktop");
        config.put("PUBLIC_DESKTOP", "true");
        config.put(
                "PUBLIC_DESKTOP_HEARTBEAT_SECONDS",
                Long.toString(properties.getHeartbeatInterval().toSeconds()));
        config.put("PUBLIC_SHARE_SNAPSHOT_ENABLED", Boolean.toString(snapshotsEnabled));
        this.script = render(config);
    }

    @GetMapping(value = "/env.js")
    public ResponseEntity<String> runtimeConfig() {
        return ResponseEntity.ok()
                .contentType(JAVASCRIPT)
                .cacheControl(CacheControl.noStore())
                .body(script);
    }

    static String render(Map<String, String> config) {
        var script = new StringBuilder("window.__RDFARCHITECT_CONFIG__ = {\n");
        config.forEach(
                (key, value) ->
                        script.append("    ")
                                .append(key)
                                .append(": ")
                                .append(jsString(value))
                                .append(",\n"));
        return script.append("};\n").toString();
    }

    static String jsString(String value) {
        var quoted = new StringBuilder("\"");
        for (var c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '<' -> quoted.append("\\u003c");
                case ' ' -> quoted.append("\\u2028");
                case ' ' -> quoted.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        quoted.append("\\u%04x".formatted((int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static String commitOfBuild() {
        try (InputStream in =
                RuntimeConfigController.class
                        .getClassLoader()
                        .getResourceAsStream("git.properties")) {
            if (in == null) {
                return "";
            }
            var properties = new Properties();
            properties.load(in);
            return properties.getProperty("git.commit.id.abbrev", "");
        } catch (IOException _) {
            return "";
        }
    }
}
