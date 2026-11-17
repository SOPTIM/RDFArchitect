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

/** Exception is thrown when a graph is to be given a name it cannot have. */
public class InvalidSchemaNameException extends GraphException {

    public InvalidSchemaNameException(String name) {
        super(
                HttpStatus.BAD_REQUEST,
                "'"
                        + name
                        + "' is not a valid schema name. A schema name must not be empty, must not"
                        + " contain any of \\ / : * ? \" < > | ; or control characters and must"
                        + " not be a name reserved by the file system.");
    }
}
