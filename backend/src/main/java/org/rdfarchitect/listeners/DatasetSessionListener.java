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

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fills each new session with the workspaces kept in the external database. Does nothing where
 * there is no such database ({@code database.enabled=false}), as in the desktop app.
 */
@Component
public class DatasetSessionListener implements HttpSessionListener {

    private final DatabasePort databasePort;
    private final DatabaseConnection databaseConnection;
    private final boolean databaseEnabled;

    public DatasetSessionListener(
            DatabasePort databasePort,
            DatabaseConnection databaseConnection,
            @Value("${database.enabled:true}") boolean databaseEnabled) {
        this.databasePort = databasePort;
        this.databaseConnection = databaseConnection;
        this.databaseEnabled = databaseEnabled;
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        if (!databaseEnabled) {
            return;
        }
        SessionContext.setSessionId(event.getSession().getId());
        databasePort.fetchFromDatabase(databaseConnection);
        SessionContext.clear();
    }
}
