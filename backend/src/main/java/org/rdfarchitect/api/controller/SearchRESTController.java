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

package org.rdfarchitect.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.cim.search.SearchFilter;
import org.rdfarchitect.cim.search.data.SearchResults;
import org.rdfarchitect.services.SearchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/search")
@RequiredArgsConstructor
public class SearchRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SearchRESTController.class);

    private final SearchUseCase searchUseCase;

    @Operation(
              summary = "Search the graph",
              description = "Searches the given graph for a specific query",
              tags = {"dataset"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200"
                        )
              }
    )
    @PostMapping
    public SearchResults search(
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The query to search for")
              @RequestParam
              String query,
              @Parameter(description = "The scope where the search is performed.")
              @RequestBody
              SearchFilter filter) {
        logger.info("Received GET request: \"/api/search\" from \"{}\".", originURL);

        var results = searchUseCase.search(query, filter);

        logger.info("Sending response to GET request: \"/api/search\" to \"{}\".", originURL);
        return results;
    }
}
