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
import org.rdfarchitect.api.dto.migration.PropertyOverview;
import org.rdfarchitect.api.dto.migration.PropertyRenamings;
import org.rdfarchitect.services.schemamigration.renamings.ConfirmPropertyRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetPropertyRenamingsUseCase;
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
@RequestMapping("/api/migrations/property-renames")
@RequiredArgsConstructor
public class PropertyRenamingsRESTController {

    private static final Logger logger = LoggerFactory.getLogger(PropertyRenamingsRESTController.class);

    private final GetPropertyRenamingsUseCase getPropertyRenamingsUseCase;
    private final ConfirmPropertyRenamingsUseCase confirmPropertyRenamingsUseCase;

    @Operation(
              summary = "migration class property overview",
              description = "Provides an overview of the properties on migration classes including added, modified, deleted and potentially renamed properties.",
              tags = {"migration"},
              responses = {@ApiResponse(
                        responseCode = "200",
                        content = @Content(
                                  mediaType = "application/json",
                                  array = @ArraySchema(schema = @Schema(implementation = PropertyOverview.class))
                        ))
              }
    )
    @GetMapping
    public List<PropertyOverview> migrationPropertiesOverview(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL) {
        logger.info("Received GET request: \"/api/migrations/property-renames\" from \"{}\".", originURL);

        var result = getPropertyRenamingsUseCase.getPropertyRenamings();

        logger.info("Sending response to GET request: \"/api/datasets/migrations/property-renames\" from \"{}\".", originURL);

        return result;
    }
    @Operation(
              summary = "confirm renamed properties",
              description = "Confirms the previously suggested renamed properties and updates the migration context accordingly.",
              tags = {"migration"}
    )
    @PostMapping
    public void confirmRenamedProperties(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The updated property renames")
              @RequestBody
              List<PropertyRenamings> propertyRenamings) {
        logger.info("Received POST request: \"/api/migrations/property-renames\" from \"{}\".", originURL);

        confirmPropertyRenamingsUseCase.confirmPropertyRenamings(propertyRenamings);

        logger.info("Sending response to POST request: \"/api/migrations/property-renames\" from \"{}\".", originURL);
    }
}
