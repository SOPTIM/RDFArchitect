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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.migration.DefaultValueView;
import org.rdfarchitect.services.schemamigration.defaults.GetDefaultValueViewsUseCase;
import org.rdfarchitect.services.schemamigration.defaults.SubmitDefaultValuesUseCase;
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
@RequestMapping("/api/migrations/default-values")
@RequiredArgsConstructor
public class DefaultValuesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(DefaultValuesRESTController.class);

    private final GetDefaultValueViewsUseCase getDefaultValueViewsUseCase;
    private final SubmitDefaultValuesUseCase submitDefaultValuesUseCase;

    @Operation(
            summary = "get default values overview",
            description =
                    "Provides an overview of attributes that require default values to be set for migration.",
            tags = {"migration"})
    @GetMapping
    public List<DefaultValueView> getDefaultValuesViews(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL) {
        logger.info(
                "Received GET request: \"/api/migrations/default-values\" from \"{}\".", originURL);

        var result = getDefaultValueViewsUseCase.getDefaultValueViews();

        logger.info(
                "Sending response to GET request: \"/api/migrations/default-values\" from \"{}\".",
                originURL);
        return result;
    }

    @Operation(
            summary = "submit default values",
            description = "Sets the default values for attributes as provided by the user.",
            tags = {"migration"})
    @PostMapping
    public void submitDefaultValues(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The updated default values for attributes.") @RequestBody
                    List<DefaultValueView> defaultValues) {
        logger.info(
                "Received POST request: \"/api/migrations/default-values\" from \"{}\".",
                originURL);

        submitDefaultValuesUseCase.submitDefaultValues(defaultValues);

        logger.info(
                "Sending response to POST request: \"/api/migrations/default-values\" from \"{}\".",
                originURL);
    }
}
