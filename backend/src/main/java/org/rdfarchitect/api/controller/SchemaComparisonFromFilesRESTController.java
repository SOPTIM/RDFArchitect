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
import org.rdfarchitect.cim.changes.PackageChange;
import org.rdfarchitect.services.SchemaComparisonUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/compare")
public class SchemaComparisonFromFilesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaComparisonFromFilesRESTController.class);

    private final SchemaComparisonUseCase schemaComparisonUseCase;

    @Operation(
              summary = "compare schemas",
              description = "Compare two given graphs",
              responses = {
                        @ApiResponse(
                                  responseCode = "200",
                                  content = @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = PackageChange.class))
                                  )
                        )
              }
    )
    @PostMapping
    public List<PackageChange> compareSchemas(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The first graph file.")
              @RequestParam("fileA")
              MultipartFile fileA,
              @Parameter(description = "The second graph file.")
              @RequestParam("fileB")
              MultipartFile fileB) {
        logger.info("Received POST request: \"/api/datasets/graphs/compare/files\" from \"{}\".", originURL);

        var changes = schemaComparisonUseCase.compareSchemas(fileA, fileB);

        logger.info("Sending response to POST request: \"/api/compare\" from \"{}\".", originURL);

        return changes;
    }
}
