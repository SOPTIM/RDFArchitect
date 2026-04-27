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

package org.rdfarchitect.exception.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Exception is thrown when access to a dataset is denied for a session. */
public class DatasetAccessDeniedException extends ResponseStatusException {

    public DatasetAccessDeniedException(String datasetName) {
        super(HttpStatus.FORBIDDEN, "Access denied to dataset: " + datasetName);
    }

    public DatasetAccessDeniedException(String sessionId, String datasetName) {
        super(
                HttpStatus.FORBIDDEN,
                "Access denied for session " + sessionId + " to dataset: " + datasetName);
    }

    public DatasetAccessDeniedException(String datasetName, Throwable cause) {
        super(HttpStatus.FORBIDDEN, "Access denied to dataset: " + datasetName, cause);
    }
}
