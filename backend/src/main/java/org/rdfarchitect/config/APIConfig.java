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

package org.rdfarchitect.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class APIConfig {

    // needed to use encoded special chars in url-parameter
    @Bean
    TomcatConnectorCustomizer connectorCustomizer() {
        return connector ->
                connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
    }

    @Bean
    OpenAPI rdfArchitectOpenAPI(AppVersionResolver appVersionResolver) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("RDFArchitect backend")
                                .version(appVersionResolver.resolveVersion())
                                .description(
                                        "This API provides utilities for editing RDFGraphs that model UML classes using the CIM standard.")
                                .license(
                                        new License()
                                                .name("Apache License 2.0")
                                                .url("https://www.apache.org/licenses/LICENSE-2.0"))
                                .contact(
                                        new Contact().url("https://www.soptim.de/").name("soptim")))
                .servers(
                        List.of(
                                new Server()
                                        .description("local hosted")
                                        .url("http://localhost:8080/")));
    }
}
