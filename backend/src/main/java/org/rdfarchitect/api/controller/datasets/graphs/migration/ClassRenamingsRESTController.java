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

import org.rdfarchitect.api.dto.migration.ResourceRenameOverview;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.services.schemamigration.renamings.ClassRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetClassRenamingsUseCase;
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
@RequestMapping("/api/migrations/class-renamings")
@RequiredArgsConstructor
public class ClassRenamingsRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(ClassRenamingsRESTController.class);

    private final ClassRenamingsUseCase classRenamingsUseCase;
    private final GetClassRenamingsUseCase getClassRenamingsUseCase;

    @Operation(
            summary = "migration class overview",
            description =
                    "Provides an overview of the migration classes including added, modified, deleted and potentially renamed classes.",
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
                                                                                ResourceRenameOverview
                                                                                        .class))))
            })
    @GetMapping
    public ResourceRenameOverview<SemanticClassChange> getClassRenamings(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL) {
        logger.info(
                "Received GET request: \"/api/migrations/class-renamings\" from \"{}\".",
                originURL);

        var classes = getClassRenamingsUseCase.getClassRenamings();

        logger.info(
                "Sending response to GET request: \"/api/datasets/migrations/class-renamings\" from \"{}\".",
                originURL);

        return classes;
    }

    @Operation(
            summary = "confirm renamed classes",
            description =
                    "Confirms the previously suggested renamed classes and updates the migration context accordingly.",
            tags = {"migration"})
    @PostMapping
    public void confirmRenamedClasses(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The updated class renamings") @RequestBody
                    List<RenameCandidate<SemanticClassChange>> classRenamings) {
        logger.info(
                "Received POST request: \"/api/migrations/class-renamings\" from \"{}\".",
                originURL);

        classRenamingsUseCase.confirmClassRenamings(classRenamings);

        logger.info(
                "Sending response to POST request: \"/api/migrations/class-renamings\" from \"{}\".",
                originURL);
    }
}
