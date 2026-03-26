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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.schemamigration.ClearMigrationContextUseCase;
import org.rdfarchitect.services.schemamigration.SetMigrationContextUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/migrations/context")
public class MigrationContextRESTController {

    private static final Logger logger = LoggerFactory.getLogger(MigrationContextRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final SetMigrationContextUseCase setMigrationContextUseCase;
    private final ClearMigrationContextUseCase clearMigrationContextUseCase;

    @Operation(
              summary = "compute migration context",
              description = "Computes the diff of two given graphs and stores it in the session for later usage in migration endpoints." +
                        "Accepts the graphs either as file uploads, GraphIdentifiers or a combination of both.",
              tags = {"migration"}
    )
    @PostMapping
    public void computeMigrationContext(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The file containing graph A. Mutually exclusive with dataset_a and graph_uri_a.")
              @RequestPart(required = false)
              MultipartFile fileA,
              @Parameter(description = "Dataset of graph A. Required together with graph_uri_a when not uploading graph A as a file.")
              @RequestParam(required = false)
              String datasetA,
              @Parameter(description = "URI of graph A. Required together with dataset_a when not uploading graph A as a file.")
              @RequestParam(required = false)
              String graphA,
              @Parameter(description = "The file containing graph B. Mutually exclusive with dataset_b and graph_uri_b.")
              @RequestPart(required = false)
              MultipartFile fileB,
              @Parameter(description = "Dataset of graph B. Required together with graph_uri_b when not uploading graph B as a file.")
              @RequestParam(required = false)
              String datasetB,
              @Parameter(description = "URI of graph B. Required together with dataset_b when not uploading graph B as a file.")
              @RequestParam(required = false)
              String graphB) {
        logger.info("Received POST request: \"/api/migrations/context\" from \"{}\".", originURL);

        // file to file
        if(fileA != null && fileB != null) {
            setMigrationContextUseCase.setMigrationContext(fileA, fileB);
        }
        // stored to stored
        else if (datasetA != null && datasetB != null && graphA != null && graphB != null) {
            var extendedGraphURIA = expandURIUseCase.expandUri(datasetA, graphA);
            var extendedGraphURIB = expandURIUseCase.expandUri(datasetB, graphB);
            setMigrationContextUseCase.setMigrationContext(new GraphIdentifier(datasetA, extendedGraphURIA), new GraphIdentifier(datasetB, extendedGraphURIB));
        }
        // file to stored
        else if (fileA != null && datasetB != null && graphB != null) {
            var extendedGraphURIB = expandURIUseCase.expandUri(datasetB, graphB);
            setMigrationContextUseCase.setMigrationContext(fileA, new GraphIdentifier(datasetB, extendedGraphURIB));
        } else {
            logger.warn("Invalid request to POST \"/api/migrations/context\" from \"{}\". Missing required parameters.", originURL);
            throw new IllegalArgumentException("Invalid request. Graphs must be provided either as files, GraphIdentifiers or a file and a GraphIdentifier.");
        }

        logger.info("Sending response to POST request: \"/api/migrations/context\" from \"{}\".", originURL);
    }

    @Operation(
              summary = "reset migration context",
              description = "Resets the current migration context, removing all stored data related to the ongoing migration session.",
              tags = {"migration"}
    )
    @DeleteMapping
    public void clearMigrationContext(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL) {
        logger.info("Received DELETE request: \"/api/migrations/context\" from \"{}\".", originURL);

        clearMigrationContextUseCase.clearMigrationContext();

        logger.info("Sending response to DELETE request: \"/api/migrations/context\" from \"{}\".", originURL);
    }
}
