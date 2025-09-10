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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;
import org.rdfarchitect.services.GetPrimitiveDatatypesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/primitiveDatatypes")
@RequiredArgsConstructor
public class PrimitiveDatatypesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(PrimitiveDatatypesRESTController.class);

    private final GetPrimitiveDatatypesUseCase getPrimitiveDatatypesUseCase;

    @Operation(
              summary = "Get primitive datatypes",
              description = "Get a list of all supported xsd datatypes.",
              tags = {"data"},
              responses = {
                        @ApiResponse(responseCode = "200",
                                     description = "A list containing the uris of xsd datatypes.",
                                     content = @Content(
                                               mediaType = "application/json",
                                               array = @ArraySchema(schema = @Schema(implementation = URI.class)))
                        )
              }
    )
    @GetMapping
    public List<URI> getPrimitiveDatatypes(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL
                                          ) {
        logger.info("Received GET request: \"/api/primitives\" from \"{}\".", originURL);

        var xsdURIList = getPrimitiveDatatypesUseCase.getPrimitiveDatatypes();

        logger.info("Sending response to GET request: \"/api/primitives\" to \"{}\".", originURL);
        return xsdURIList;
    }
}
