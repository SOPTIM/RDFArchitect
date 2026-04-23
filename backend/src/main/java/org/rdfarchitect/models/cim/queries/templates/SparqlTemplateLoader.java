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

package org.rdfarchitect.models.cim.queries.templates;

import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SparqlTemplateLoader {
    public static ParameterizedSparqlString loadTemplate(String path) {
        try {
            var resource = new ClassPathResource("sparql-templates/" + path + ".sparql");
            return new ParameterizedSparqlString(readTemplate(resource));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SPARQL template: " + path, e);
        }
    }

    static String readTemplate(Resource resource) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
