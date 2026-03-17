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

package org.rdfarchitect.api.controller.datasets.graphs.classes.enumentries;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.classes.enumentries.ReplaceOrCreateEnumEntryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/enumentries/{enumEntryUUID}")
@RequiredArgsConstructor
public class ClassEnumEntriesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassEnumEntriesRESTController.class);
    private final ExpandURIUseCase expandURIUseCase;

    private final ReplaceOrCreateEnumEntryUseCase replaceOrCreateEnumEntryUseCase;

    @Operation(
              summary = "Replace enum entry",
              description = "Replaces the enum entry for a given enum URI and labels",
              tags = {"enum"},
              responses = {@ApiResponse(responseCode = "200")}
    )
    @PutMapping
    public UUID replaceEnumEntry(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName,
              @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
              @PathVariable
              String graphURI,
              @Parameter(description = "The uuid of the enum.")
              @PathVariable
              String classUUID,
              @Parameter(description = "The uuid of the enum entry.")
              @PathVariable
              String enumEntryUUID,
              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        required = true,
                        description = "The new enum entry.")
              @RequestBody EnumEntryDTO enumEntry) {
        logger.info("Received PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/enumentries/{{}}\" from \"{}\".", datasetName, graphURI, classUUID, enumEntryUUID,
                    originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);

        var newEnumEntryUUID = replaceOrCreateEnumEntryUseCase.replaceOrCreateEnumEntry(
                  new GraphIdentifier(datasetName, extendedGraphURI),
                  enumEntry
                                                                                       );

        logger.info("Sending response to PUT request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/enumentries/{{}}\" from \"{}\".", datasetName, graphURI, classUUID,
                    enumEntryUUID, originURL);
        return newEnumEntryUUID;
    }
}
