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
import lombok.RequiredArgsConstructor;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatasetSessionListener implements HttpSessionListener {

    private final DatabasePort databasePort;
    private final DatabaseConnection databaseConnection;
    private final ChangeLogUseCase changeLogUseCase;

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        SessionContext.setSessionId(event.getSession().getId());
        databasePort.fetchFromDatabase(databaseConnection);
        var datasets = databasePort.listDatasets();
        for (var dataset : datasets) {
            var graphUris = databasePort.listGraphUris(dataset);
            for (var graphUri : graphUris) {
                var graphIdentifier = new GraphIdentifier(dataset, graphUri);
                changeLogUseCase.recordChange(
                          graphIdentifier,
                          new ChangeLogEntry("Imported graph into dataset '" + dataset + "' with graph URI '"
                                                       + graphUri + "'.", databasePort.getGraphWithContext(graphIdentifier).getRdfGraph().getLastDelta())
                                             );
            }
        }

        SessionContext.clear();
    }
}
