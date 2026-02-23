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

package org.rdfarchitect.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.ontology.OntologyField;
import org.rdfarchitect.services.select.ontology.GetKnownOntologyFieldsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ontology-fields")
@RequiredArgsConstructor
public class OntologyKnownInformationRESTController {

    private static final Logger logger = LoggerFactory.getLogger(OntologyKnownInformationRESTController.class);

    private final GetKnownOntologyFieldsUseCase readOntologyUseCase;

    @Operation(
              summary = "Get known ontology fields",
              description = "Get a list of all known ontology fields.",
              tags = {"ontology"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  schema = @Schema(implementation = OntologyField.class)))}
    )
    @GetMapping
    public List<OntologyField> getOntology(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL) {
        logger.info("Received GET request: \"/api/ontology-information\" from \"{}\".", originURL);

        var knownOntologyFields = readOntologyUseCase.getKnownOntologyFields();

        logger.info("Sending response to GET request: \"/api/ontology-fields\" to \"{}\".", originURL);
        return knownOntologyFields;
    }
}
