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

package org.rdfarchitect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** * Exports the springdoc OpenAPI document to a deterministic JSON file so the frontend can generate * its API client offline (in CI and in Docker) without a running backend. * * <p>Runs as a standalone Maven goal: {@code mvn compile exec:java@export-openapi}. The output * path defaults to {@code ../frontend/openapi.json} (relative to the backend module) and can be * overridden with the first program argument or {@code -Dopenapi.export.path=...}. * * <p>Boots the full Spring application on an ephemeral port (server.port=0), fetches the spec, * normalizes it, writes it to disk, and shuts the context down. CI runs this and diffs the result * so the committed spec can never drift from the controllers. */
public final class OpenApiSpecExport {

    private static final String DEFAULT_OUTPUT_PATH = "../frontend/openapi.json";

    private OpenApiSpecExport() {}

    public static void main(String[] args) throws IOException {
        // Use an ephemeral port so we never collide with a developer's running backend.
        System.setProperty("server.port", "0");

        try (ConfigurableApplicationContext ctx = SpringApplication.run(Launcher.class, args)) {
            if (!(ctx instanceof WebServerApplicationContext webCtx)) {
                throw new IllegalStateException(
                        "Expected a WebServerApplicationContext but got " + ctx.getClass());
            }
            int port = Objects.requireNonNull(webCtx.getWebServer()).getPort();
            String rawSpec =
                    RestClient.create()
                            .get()
                            .uri("http://localhost:" + port + "/v3/api-docs")
                            .retrieve()
                            .body(String.class);

            if (rawSpec == null || !rawSpec.contains("\"openapi\"")) {
                throw new IllegalStateException(
                        "Unexpected response from /v3/api-docs: " + rawSpec);
            }

            String formattedSpec = formatDeterministically(rawSpec);

            Path target = resolveTargetPath(args);
            Path parent = target.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, formattedSpec, StandardCharsets.UTF_8);

            System.out.println("Wrote OpenAPI spec to " + target.toAbsolutePath().normalize());
        }
        System.exit(0);
    }

    private static Path resolveTargetPath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return Path.of(System.getProperty("openapi.export.path", DEFAULT_OUTPUT_PATH));
    }

    /**     * Re-serializes the spec with sorted keys and a fixed {@code \n} indenter so the output is     * stable across runs and operating systems. The {@code servers} entry is dropped because it     * reflects the request URL and is overridden at runtime by the frontend client ({@code     * src/lib/api/hey-api.ts}); keeping it adds no value to the committed contract.     */
    private static String formatDeterministically(String rawSpec) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);

        Map<String, Object> spec = mapper.readValue(rawSpec, new TypeReference<>() {});
        spec.remove("servers");
        sortRequiredArrays(spec);
        return mapper.writer(printer).writeValueAsString(spec) + "\n";
    }

    /**     * Sorts every {@code required} string array in the document. springdoc derives this list from     * field reflection, whose order is not guaranteed across JVMs; {@code required} is an unordered     * set in OpenAPI, so sorting it makes the committed contract robust against that variance.     */
    private static void sortRequiredArrays(Object node) {
        if (node instanceof Map<?, ?> map) {
            Object required = map.get("required");
            if (required instanceof List<?> values
                    && values.stream().allMatch(String.class::isInstance)) {
                List<String> sorted = values.stream().map(String.class::cast).sorted().toList();
                @SuppressWarnings("unchecked")
                Map<String, Object> writableMap = (Map<String, Object>) map;
                writableMap.put("required", sorted);
            }
            map.values().forEach(OpenApiSpecExport::sortRequiredArrays);
        } else if (node instanceof List<?> values) {
            values.forEach(OpenApiSpecExport::sortRequiredArrays);
        }
    }
}
