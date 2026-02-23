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

package org.rdfarchitect.api.controller.datasets.graphs.classes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.cim.relations.ClassRelationsDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.select.GetClassesReferencingThisClassUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/datasets/{datasetName}/graphs/{graphURI}/classes/{classUUID}/referencedByClasses")
@RequiredArgsConstructor
public class ClassReferencedByClassesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(ClassReferencedByClassesRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final GetClassesReferencingThisClassUseCase getClassesReferencingThisClassUseCase;

    @Operation(
            summary = "Get classes referencing this class",
            description = "Get all classes referencing this class.",
            tags = {"class"}
    )
    @GetMapping
    public ClassRelationsDTO getClassesReferencingThisClass(
            @Parameter(description = "The name/url of the inquirer.")
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false, defaultValue = "unknown")
            String originURL,
            @Parameter(description = "The literal name of the dataset.")
            @PathVariable
            String datasetName,
            @Parameter(description = "The url encoded uri of the graph, or \"default\" to access the default graph.")
            @PathVariable
            String graphURI,
            @Parameter(description = "The uuid of the class.")
            @PathVariable
            String classUUID) {
        logger.info("Received GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/referencedByClasses\" from \"{}\".", datasetName, graphURI, classUUID, originURL);

        var extendedGraphURI = expandURIUseCase.expandUri(datasetName, graphURI);
        var graphIdentifier = new GraphIdentifier(datasetName, extendedGraphURI);

        var classesReferencingThisClass = getClassesReferencingThisClassUseCase.getClassesReferencingThisClass(graphIdentifier, UUID.fromString(classUUID));

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/graphs/{{}}/classes/{{}}/referencedByClasses\" to \"{}\".", datasetName, graphURI, classUUID, originURL);
        return classesReferencingThisClass;
    }
}
