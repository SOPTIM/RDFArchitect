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

/**
 * Thrown when a restore cannot be carried out as asked, for a reason the user can act on — a schema
 * that did not exist yet at that version, or one the workspace no longer holds. Nothing went wrong
 * and nothing was changed; the message says what stands in the way.
 *
 * <p>Answered with 422 rather than 409, which tells a client that the workspace moved since the
 * restore was confirmed and the question has to be asked again.
 */
public class RestoreRefusedException extends GraphVersionControlException {

    public RestoreRefusedException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
