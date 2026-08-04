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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionRESTControllerTest {

    private SessionRESTController controller;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        controller = new SessionRESTController();
        session = mock(HttpSession.class);
    }

    @Test
    void getSession_returnsTheSessionId() {
        when(session.getId()).thenReturn("3DA842A28B7F3DE99EA014ACBFBB420F");

        assertThat(controller.getSession(session).getId())
                .isEqualTo("3DA842A28B7F3DE99EA014ACBFBB420F");
    }

    @Test
    void resetSession_invalidatesTheSession() {
        when(session.getId()).thenReturn("some-session");

        controller.resetSession(session);

        verify(session).invalidate();
    }
}
