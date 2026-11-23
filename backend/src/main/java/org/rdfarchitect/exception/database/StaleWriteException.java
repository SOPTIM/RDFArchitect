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
 * Thrown when a write was made to a version of a resource that is no longer the stored one — it was
 * changed elsewhere since the client read it. Answered with 412 so a client can tell it apart from
 * a 409, which says the write itself is not allowed.
 */
public class StaleWriteException extends DatabaseException {

    public StaleWriteException(String errorMessage) {
        super(HttpStatus.PRECONDITION_FAILED, errorMessage);
    }
}
