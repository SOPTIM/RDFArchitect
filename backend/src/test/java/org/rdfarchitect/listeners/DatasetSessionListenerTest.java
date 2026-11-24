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

package org.rdfarchitect.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpSessionEvent;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.springframework.mock.web.MockHttpSession;

class DatasetSessionListenerTest {

    private final DatabasePort databasePort = mock(DatabasePort.class);
    private final DatabaseConnection databaseConnection = mock(DatabaseConnection.class);

    @Test
    void sessionCreated_databaseEnabled_fillsSessionFromDatabase() {
        new DatasetSessionListener(databasePort, databaseConnection, true)
                .sessionCreated(new HttpSessionEvent(new MockHttpSession()));

        verify(databasePort).fetchFromDatabase(databaseConnection);
    }

    @Test
    void sessionCreated_databaseDisabled_leavesSessionEmpty() {
        new DatasetSessionListener(databasePort, databaseConnection, false)
                .sessionCreated(new HttpSessionEvent(new MockHttpSession()));

        verify(databasePort, never()).fetchFromDatabase(any());
    }
}
