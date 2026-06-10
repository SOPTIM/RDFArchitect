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

package org.rdfarchitect.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/session")
public class SessionRESTController {

    private static final Logger logger = LoggerFactory.getLogger(SessionRESTController.class);

    @Operation(
            summary = "Reset session",
            description = "Invalidates the current session, discarding all unsaved changes. A new session is created on the next request.",
            tags = {"session"},
            responses = {@ApiResponse(responseCode = "204")})
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetSession(HttpSession session) {
        logger.info("Invalidating session {} on user request.", session.getId());
        session.invalidate();
    }
}
