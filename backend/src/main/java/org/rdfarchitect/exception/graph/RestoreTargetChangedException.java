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
 * Thrown when a restore was confirmed with the workspace at one point in its history but it has
 * moved in the meantime, so that the restore would take back or discard something the user was not
 * shown.
 */
public class RestoreTargetChangedException extends GraphException {

    public RestoreTargetChangedException() {
        super(
                HttpStatus.CONFLICT,
                "The workspace changed since the restore was confirmed; nothing was restored.");
    }
}
