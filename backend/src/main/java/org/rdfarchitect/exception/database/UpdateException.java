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
 * Exception is thrown when there is an issue with an update.
 */
public class UpdateException extends DatabaseException {

    public UpdateException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public UpdateException(String errorMessage) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
    }

    public UpdateException(String errorMessage, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage, cause);
    }
}
