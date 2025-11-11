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

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;

//TODO: Openapi-Beschreibung, license wurde schon ergänzt
@OpenAPIDefinition(
          info = @Info(
                    title = "RDFArchitect backend",
                    version = "0.13.0",
                    description = "This API provides utilities for editing RDFGraphs that model UML classes using the CIM standard.",
                    license = @License(name = "Apache License 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
                    contact = @Contact(url = "https://www.soptim.de/", name = "soptim")
          ),
          servers = {
                    @Server(
                              description = "local hosted",
                              url = "http://localhost:8080/"
                    )
          }
)
@SpringBootApplication
@ServletComponentScan
@NoArgsConstructor
@ToString
@Log4j2
public class Launcher {

    public static void main(String[] argv) {
        SpringApplication application = new SpringApplication(Launcher.class);
        application.run(argv);
    }
}
