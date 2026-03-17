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

package org.rdfarchitect.exception.handlers;

import jakarta.servlet.http.HttpServletRequest;
import org.rdfarchitect.exception.database.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GenericExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GenericExceptionHandler.class);

    private void logException(Exception e, WebRequest request) {
        log.error("Error message: {}", e.getMessage(), e);

        HttpServletRequest req = ((ServletWebRequest) request).getRequest();
        String requestURL = req.getRequestURI();

        String requestRemoteAddr = req.getHeader(HttpHeaders.ORIGIN) != null && !req.getHeader(HttpHeaders.ORIGIN).isEmpty() ?
                                   req.getHeader(HttpHeaders.ORIGIN) : req.getRemoteAddr() + ":" + req.getRemotePort();

        log.warn("Sending error response to GET request \"{}\" from \"{}\"", requestURL, requestRemoteAddr);
    }

    @ExceptionHandler(value = {DatabaseException.class})
    protected ResponseStatusException handleExceptionInternal(ResponseStatusException e, WebRequest request) {
        logException(e, request);

        return e;
    }

    @ExceptionHandler(value = {Exception.class})
    protected ResponseStatusException handleGenericException(Exception e, WebRequest request) {
        logException(e, request);

        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
