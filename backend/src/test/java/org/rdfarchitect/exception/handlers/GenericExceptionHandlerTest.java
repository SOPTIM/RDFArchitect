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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** A client's mistake is answered, not reported as a fault of the server. */
class GenericExceptionHandlerTest {

    @RestController
    static class Failing {

        @GetMapping("/missing")
        String missing() {
            throw new ResourceNotFoundException("No such thing.");
        }

        @GetMapping("/broken")
        String broken() {
            throw new DataAccessException("Disk on fire.");
        }
    }

    /** Keeps what was logged, level and throwable included. */
    static final class Recording extends AbstractAppender {

        final List<LogEvent> events = new ArrayList<>();

        Recording() {
            super("recording", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private final Logger logger = (Logger) LogManager.getLogger(GenericExceptionHandler.class);
    private final Recording appender = new Recording();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new Failing())
                        .setControllerAdvice(new GenericExceptionHandler())
                        .build();
    }

    @AfterEach
    void tearDown() {
        logger.removeAppender(appender);
    }

    @Test
    void aNotFoundIsLoggedWithoutAStackTraceOrAnError() throws Exception {
        mockMvc.perform(get("/missing")).andExpect(status().isNotFound());

        assertThat(appender.events)
                .isNotEmpty()
                .allSatisfy(
                        event -> {
                            assertThat(event.getLevel().isMoreSpecificThan(Level.WARN)).isFalse();
                            assertThat(event.getThrown()).isNull();
                        });
    }

    @Test
    void aServerFaultIsStillAnErrorWithItsStackTrace() throws Exception {
        mockMvc.perform(get("/broken")).andExpect(status().isInternalServerError());

        assertThat(appender.events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                            assertThat(event.getThrown()).isNotNull();
                        });
    }
}
