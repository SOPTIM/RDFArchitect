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

package org.rdfarchitect.api.controller.datasets.graphs.classes.paste;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.PasteClassesRequestDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.update.classes.CopyClassUseCase;
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
@RequestMapping("api/datasets/{targetDatasetName}/graphs/{targetGraphURI}/paste")
@RequiredArgsConstructor
public class PasteRESTController {

    private static final Logger logger = LoggerFactory.getLogger(PasteRESTController.class);

    private final ExpandURIUseCase expandURIUseCase;
    private final CopyClassUseCase copyClassUseCase;

    @Operation(
            summary = "paste classes",
            description =
                    "Create copies of one or more previously copied classes in the target graph.")
    @PostMapping
    public List<CopyClassResponseDTO> pasteClasses(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the target dataset.") @PathVariable
                    String targetDatasetName,
            @Parameter(
                            description =
                                    "The url encoded uri of the target graph, or \"default\" to access the default graph.")
                    @PathVariable
                    String targetGraphURI,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description =
                                    "The target package, copy options and the list of source classes to paste.")
                    @RequestBody
                    PasteClassesRequestDTO pasteRequest) {
        logger.info(
                "Received POST request: \"/api/datasets/{{}}/graphs/{{}}/paste\" for {} class(es) from \"{}\".",
                targetDatasetName,
                targetGraphURI,
                pasteRequest.getSources() == null ? 0 : pasteRequest.getSources().size(),
                originURL);

        var targetExtendedGraphURI = expandURIUseCase.expandUri(targetDatasetName, targetGraphURI);
        var targetGraphIdentifier = new GraphIdentifier(targetDatasetName, targetExtendedGraphURI);

        var responses = copyClassUseCase.copyClasses(pasteRequest, targetGraphIdentifier);

        logger.info(
                "Sending response to POST request: \"/api/datasets/{{}}/graphs/{{}}/paste\" to \"{}\".",
                targetDatasetName,
                targetGraphURI,
                originURL);
        return responses;
    }
}
