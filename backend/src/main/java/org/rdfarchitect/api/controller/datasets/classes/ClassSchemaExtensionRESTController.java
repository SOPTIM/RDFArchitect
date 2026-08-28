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

package org.rdfarchitect.api.controller.datasets.classes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.ClassExtensionResultDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ClassExtensionUseCase;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/datasets/{datasetName}/classes/extendToSchema")
@RequiredArgsConstructor
public class ClassSchemaExtensionRESTController {

    private static final Logger logger =
            LoggerFactory.getLogger(ClassSchemaExtensionRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;

    private final ClassExtensionUseCase classExtensionUseCase;

    public record ExtendToSchemaRequest(
            String graphUri, List<String> classUUIDs, boolean withInheritance) {}

    @Operation(
            summary = "Extend classes into another schema",
            description =
                    "Creates a stub of every given class in another graph of the same dataset, optionally stubbing their superclasses as well. The classes may come from different graphs and are addressed either by the uuid they carry there or by the uuid of their merged class. Classes that are already defined in the target graph are left untouched.",
            tags = {"class"})
    @PostMapping
    public List<ClassExtensionResultDTO> extendToSchema(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String datasetName,
            @RequestBody ExtendToSchemaRequest request) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/classes/extendToSchema\" from \"{}\".",
                datasetName,
                originURL);

        var targetGraphIdentifier =
                new GraphIdentifier(
                        datasetName, expandURIUseCase.expandUri(datasetName, request.graphUri()));

        var results =
                classExtensionUseCase.extendClasses(
                        datasetName,
                        request.classUUIDs(),
                        targetGraphIdentifier,
                        request.withInheritance());

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/classes/extendToSchema\" to \"{}\".",
                datasetName,
                originURL);
        return results;
    }
}
