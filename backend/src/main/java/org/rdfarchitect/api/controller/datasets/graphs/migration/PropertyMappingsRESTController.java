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

package org.rdfarchitect.api.controller.datasets.graphs.migration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.migration.ClassPropertyMappingOverview;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappings;
import org.rdfarchitect.services.schemamigration.renamings.ConfirmPropertyMappingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetPropertyMappingsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/migrations/property-mappings")
@RequiredArgsConstructor
public class PropertyMappingsRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(PropertyMappingsRESTController.class);

    private final GetPropertyMappingsUseCase getPropertyMappingsUseCase;
    private final ConfirmPropertyMappingsUseCase confirmPropertyMappingsUseCase;

    @Operation(
            summary = "migration class property overview",
            description =
                    "Provides an overview of the properties on migration classes including added, deleted and potentially remapped properties.",
            tags = {"migration"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array =
                                                @ArraySchema(
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                ClassPropertyMappingOverview
                                                                                        .class))))
            })
    @GetMapping
    public List<ClassPropertyMappingOverview> migrationPropertiesOverview(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL) {
        logger.info(
                "Received GET request: \"/api/migrations/property-mappings\" from \"{}\".",
                originURL);

        var result = getPropertyMappingsUseCase.getPropertyMappings();

        logger.info(
                "Sending response to GET request: \"/api/datasets/migrations/property-mappings\" from \"{}\".",
                originURL);

        return result;
    }

    @Operation(
            summary = "confirm remapped properties",
            description =
                    "Confirms the previously suggested renamed properties and updates the migration context accordingly.",
            tags = {"migration"})
    @PostMapping
    public void confirmPropertyMappings(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The updated property mappings") @RequestBody
                    List<ClassPropertyMappings> classPropertyMappings) {
        logger.info(
                "Received POST request: \"/api/migrations/property-mappings\" from \"{}\".",
                originURL);

        confirmPropertyMappingsUseCase.confirmPropertyMappings(classPropertyMappings);

        logger.info(
                "Sending response to POST request: \"/api/migrations/property-mappings\" from \"{}\".",
                originURL);
    }
}
