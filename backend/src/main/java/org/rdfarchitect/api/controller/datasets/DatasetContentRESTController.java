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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.services.select.GetDatasetSchemaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/datasets/{datasetName}/content")
@RequiredArgsConstructor
public class DatasetContentRESTController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetContentRESTController.class);

    private final GetDatasetSchemaUseCase getDatasetSchemaUseCase;

    @Operation(
              summary = "export dataset",
              description = "Export a file storing all RDFSchema graphs from a dataset",
              tags = {"graph"},
              responses = {
                        @ApiResponse(
                                  responseCode = "200",
                                  content = {
                                            @Content(mediaType = "application/trig"),
                                            @Content(mediaType = "application/n-quads")
                                  })
              }
    )
    @GetMapping
    public ResponseEntity<byte[]> getDatasetSchema(
              @Parameter(description = "The requested Datatype.", hidden = true)
              @RequestHeader("Accept")
              String acceptHeader,
              @Parameter(description = "The name/url of the inquirer.")
              @RequestHeader(value = "origin", required = false, defaultValue = "unknown")
              String originURL,
              @Parameter(description = "The literal name of the dataset.")
              @PathVariable
              String datasetName) {
        logger.info("Received GET request: \"/api/datasets/{{}}/content\" from \"{}\".", datasetName, originURL);

        var format = getRdfFormat(acceptHeader);

        var fileName = datasetName + "." + getRdfFormat(acceptHeader).getLang().getFileExtensions().getFirst();

        var outStream = getDatasetSchemaUseCase.getDatasetSchema(datasetName, format);
        var headers = new HttpHeaders();
        headers.setAccessControlExposeHeaders(List.of("Content-Disposition"));
        var body = ResponseEntity.ok()
                                 .headers(headers)
                                 .header(HttpHeaders.CONTENT_DISPOSITION, fileName)
                                 .body(outStream.toByteArray());

        logger.info("Sending response to GET request: \"/api/datasets/{{}}/content\" to \"{}\".", datasetName, originURL);
        return body;
    }

    private final Map<String, RDFFormat> supportedFormats = Map.ofEntries(
              new AbstractMap.SimpleEntry<>("application/trig", RDFFormat.TRIG),
              new AbstractMap.SimpleEntry<>("application/n-quads", RDFFormat.NQUADS)
                                                                         );

    private RDFFormat getRdfFormat(String acceptHeader) {
        for (var entry : supportedFormats.entrySet()) {
            if (acceptHeader.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("unsupported Media Type");
    }
}
