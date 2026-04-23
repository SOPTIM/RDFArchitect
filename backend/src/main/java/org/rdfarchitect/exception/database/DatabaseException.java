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
import org.springframework.web.server.ResponseStatusException;

/** Base exception for all database-related exceptions. */
public abstract class DatabaseException extends ResponseStatusException {

    protected DatabaseException(HttpStatus status) {
        super(status);
    }

    protected DatabaseException(HttpStatus status, String reason) {
        super(status, reason);
    }

    protected DatabaseException(HttpStatus status, String reason, Throwable cause) {
        super(status, reason, cause);
    }
}
