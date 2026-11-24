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

package org.rdfarchitect.api.controller.datasets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.models.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.services.select.ListPrefixesUseCase;
import org.rdfarchitect.services.update.dataset.ReplaceNamespacesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/workspaces/{workspaceName}/namespaces")
@RequiredArgsConstructor
public class NamespacesRESTController {

    private static final Logger logger = LoggerFactory.getLogger(NamespacesRESTController.class);

    private final ListPrefixesUseCase listNamespaceUseCase;
    private final ReplaceNamespacesUseCase replaceNamespacesUseCase;

    @Operation(
            summary = "List namespaces",
            description = "Get a list of namespaces stored in a specified dataset.",
            tags = {"dataset"},
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
                                                                                CIMPrefixPair
                                                                                        .class))))
            })
    @GetMapping
    public List<CIMPrefixPair> listNamespaces(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName) {
        logger.info(
                "Received GET request: \"/api/workspaces/{{}}/namespaces\" from \"{}\".",
                workspaceName,
                originURL);

        var result = listNamespaceUseCase.listPrefixes(workspaceName);

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/namespaces\" from \"{}\".",
                workspaceName,
                originURL);
        return result;
    }

    @Operation(
            summary = "List formatted namespaces",
            description =
                    "Get a list of namespaces stored in a specified dataset formatted in a specified format.",
            tags = {"dataset"})
    @GetMapping("/{format:ttl}")
    public String listFormattedNamespaces(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The format of the namespaces.") @PathVariable String format) {
        logger.info(
                "Received GET request: \"/api/workspaces/{{}}/namespaces?format=ttl\" from \"{}\".",
                workspaceName,
                originURL);

        var result = listNamespaceUseCase.listFormattedPrefixes(workspaceName, format);

        logger.info(
                "Sending response to GET request: \"/api/workspaces/{{}}/namespaces?format=ttl\" from \"{}\".",
                workspaceName,
                originURL);
        return result;
    }

    @Operation(
            summary = "Replace namespaces",
            description = "Replace all namespaces of a specified dataset.",
            tags = {"dataset"},
            responses = {@ApiResponse(responseCode = "200")})
    @PutMapping
    public String replaceNamespaces(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The new Namespaces.") @RequestBody
                    List<CIMPrefixPair> namespaces) {
        logger.info(
                "Received PUT request: \"/api/workspaces/{{}}/namespaces\" from \"{}\".",
                workspaceName,
                originURL);

        replaceNamespacesUseCase.replaceNamespaces(workspaceName, namespaces);

        logger.info(
                "Sending response to POST request: \"/api/workspaces/{{}}/namespaces\" from \"{}\".",
                workspaceName,
                originURL);
        return Response.SUCCESS;
    }
}
