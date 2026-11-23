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

package org.rdfarchitect.exception.database;

import org.springframework.http.HttpStatus;

/**
 * Thrown when content sent by the client cannot be read.
 *
 * <p>A request body that does not parse is the client's problem, not a fault of this server, and
 * the difference matters to whoever is looking at the response: a 500 says "something broke, try
 * again or report it", while a 400 with the parser's own message says "line 3 of what you sent has
 * an undefined prefix", which is something the user can act on.
 */
public class InvalidContentException extends DatabaseException {

    public InvalidContentException(String errorMessage) {
        super(HttpStatus.BAD_REQUEST, errorMessage);
    }
}
