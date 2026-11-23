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
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.DatasetDTO;
import org.rdfarchitect.services.select.ListDatasetsUseCase;
import org.rdfarchitect.services.update.dataset.CreateDatasetUseCase;
import org.rdfarchitect.services.update.dataset.DeleteDatasetUseCase;
import org.rdfarchitect.services.update.dataset.RenameDatasetUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/workspaces")
@RequiredArgsConstructor
public class DatasetRESTController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetRESTController.class);

    private final ListDatasetsUseCase listDatasetsUseCase;
    private final CreateDatasetUseCase createDatasetUseCase;
    private final DeleteDatasetUseCase deleteDatasetUseCase;
    private final RenameDatasetUseCase renameDatasetUseCase;

    @Operation(
            summary = "List datasets",
            description =
                    "Lists all non-snapshots datasets including their prefixes and read-only status.",
            tags = {"dataset"},
            responses = {@ApiResponse(responseCode = "200")})
    @GetMapping
    public List<DatasetDTO> listDatasets(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL) {
        logger.info("Received GET request: \"/api/workspaces\" from \"{}\".", originURL);

        var res = listDatasetsUseCase.listDatasets();

        logger.info("Sending response to GET request: \"/api/workspaces\" to \"{}\".", originURL);
        return res;
    }

    @Operation(
            summary = "Create dataset",
            description = "Creates an empty dataset without any graphs.",
            tags = {"dataset"},
            responses = {
                @ApiResponse(responseCode = "200"),
                @ApiResponse(responseCode = "409", description = "Dataset already exists")
            })
    @PutMapping("/{workspaceName}")
    public String createDataset(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName) {
        logger.info(
                "Received PUT request: \"/api/workspaces/{{}}\" from \"{}\".",
                workspaceName,
                originURL);

        createDatasetUseCase.createDataset(workspaceName);

        logger.info(
                "Sending response to PUT request: \"/api/workspaces/{{}}\" to \"{}\".",
                workspaceName,
                originURL);
        return Response.SUCCESS;
    }

    @Operation(
            summary = "Rename dataset",
            description = "Renames a dataset, keeping all of its graphs, diagrams and namespaces.",
            tags = {"dataset"},
            responses = {@ApiResponse(responseCode = "200")})
    @PostMapping("/{workspaceName}/rename")
    public String renameDataset(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName,
            @Parameter(description = "The literal name to rename the dataset to.") @RequestParam
                    String newWorkspaceName) {
        logger.info(
                "Received POST request: \"/api/workspaces/{{}}/rename\" from \"{}\".",
                workspaceName,
                originURL);

        renameDatasetUseCase.renameDataset(workspaceName, newWorkspaceName);

        logger.info(
                "Sending response to POST request: \"/api/workspaces/{{}}/rename\" to \"{}\".",
                workspaceName,
                originURL);
        return Response.SUCCESS;
    }

    @Operation(
            summary = "Delete dataset",
            description = "Deletes a dataset including all of its graphs.",
            tags = {"dataset"},
            responses = {@ApiResponse(responseCode = "200")})
    @DeleteMapping("/{workspaceName}")
    public String deleteDataset(
            @Parameter(description = "The name/url of the inquirer.")
                    @RequestHeader(
                            value = HttpHeaders.ORIGIN,
                            required = false,
                            defaultValue = "unknown")
                    String originURL,
            @Parameter(description = "The literal name of the dataset.") @PathVariable
                    String workspaceName) {
        logger.info(
                "Received DELETE request: \"/api/workspaces/{{}}\" from \"{}\".",
                workspaceName,
                originURL);

        deleteDatasetUseCase.deleteDataset(workspaceName);

        logger.info(
                "Sending response to DELETE request: \"/api/workspaces/{{}}\" to \"{}\".",
                workspaceName,
                originURL);
        return Response.SUCCESS;
    }
}
