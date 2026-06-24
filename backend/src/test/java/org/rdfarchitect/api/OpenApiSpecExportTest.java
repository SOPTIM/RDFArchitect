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

package org.rdfarchitect.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Exports the springdoc OpenAPI document to a deterministic JSON file so the frontend can generate
 * its API client offline (in CI and in Docker) without a running backend.
 *
 * <p>Disabled by default; enabled with {@code -Dopenapi.export=true}. The output path defaults to
 * {@code ../frontend/openapi.json} (relative to the backend module) and can be overridden with
 * {@code -Dopenapi.export.path=...}. Backend CI runs this and diffs the result so the committed
 * spec can never drift from the controllers.
 *
 * <p>Uses the default (mock) web environment and builds {@link MockMvc} from the context, matching
 * the other {@code @SpringBootTest}s. A real server environment is avoided on purpose: it would
 * start the embedded container and eagerly instantiate {@code @ServletComponentScan} components.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "openapi.export", matches = "true")
class OpenApiSpecExportTest {

    private static final String DEFAULT_OUTPUT_PATH = "../frontend/openapi.json";

    @Autowired private WebApplicationContext webApplicationContext;

    @Test
    void exportsOpenApiSpec() throws Exception {
        MockMvc mockMvc = webAppContextSetup(webApplicationContext).build();

        String rawSpec =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(rawSpec).contains("\"openapi\"");

        String formattedSpec = formatDeterministically(rawSpec);

        Path target = Path.of(System.getProperty("openapi.export.path", DEFAULT_OUTPUT_PATH));
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, formattedSpec, StandardCharsets.UTF_8);
    }

    /**
     * Re-serializes the spec with sorted keys and a fixed {@code \n} indenter so the output is
     * stable across runs and operating systems. The {@code servers} entry is dropped because it
     * reflects the request URL and is overridden at runtime by the frontend client ({@code
     * src/lib/api/hey-api.ts}); keeping it adds no value to the committed contract.
     */
    private static String formatDeterministically(String rawSpec) throws Exception {
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

    /**
     * Sorts every {@code required} string array in the document. springdoc derives this list from
     * field reflection, whose order is not guaranteed across JVMs; {@code required} is an unordered
     * set in OpenAPI, so sorting it makes the committed contract robust against that variance.
     */
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
            map.values().forEach(OpenApiSpecExportTest::sortRequiredArrays);
        } else if (node instanceof List<?> values) {
            values.forEach(OpenApiSpecExportTest::sortRequiredArrays);
        }
    }
}
