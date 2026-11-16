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

/** Exception is thrown when a namespace supplied for an import cannot name a resource. */
public class InvalidNamespaceException extends GraphException {

    private InvalidNamespaceException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /** The namespace cannot be put in front of a name to make an IRI of it. */
    public static InvalidNamespaceException notUsable(String namespace) {
        return new InvalidNamespaceException(
                "'"
                        + namespace
                        + "' is not a usable namespace: it has to be an IRI ending in # or /.");
    }
}
