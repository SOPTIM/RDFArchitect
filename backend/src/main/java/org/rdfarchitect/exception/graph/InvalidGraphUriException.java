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

package org.rdfarchitect.exception.graph;

import org.springframework.http.HttpStatus;

/** Exception is thrown when a graph URI requested for an import cannot be stored. */
public class InvalidGraphUriException extends GraphException {

    private InvalidGraphUriException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /** The graph cannot be stored under this URI. */
    public static InvalidGraphUriException notUsable(String graphUri) {
        return new InvalidGraphUriException("'" + graphUri + "' is not a usable graph URI.");
    }
}
