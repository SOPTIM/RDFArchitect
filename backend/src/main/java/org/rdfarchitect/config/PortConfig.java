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

package org.rdfarchitect.config;

import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.SnapshotPort;
import org.rdfarchitect.database.inmemory.InMemoryDatabase;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.snapshots.FusekiSnapshotAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortConfig {

    @Bean
    public DatabasePort databasePort(InMemoryDatabase inMemoryDatabase) {
        return new InMemoryDatabaseAdapter(inMemoryDatabase);
    }

    @Bean
    public SnapshotPort snapshotPort(
            DatabasePort databasePort,
            DatabaseConnection databaseConnection,
            DatabaseConfig databaseConfig) {
        return new FusekiSnapshotAdapter(databasePort, databaseConnection, databaseConfig);
    }
}
