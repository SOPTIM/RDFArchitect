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

import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.services.shacl.validation.SchemaIndexCache;
import org.rdfarchitect.services.shacl.validation.ShapesValidationService;
import org.rdfarchitect.services.shacl.validation.ShapesValidationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShapesValidationConfig {

    /**
     * Shared deliberately: indexing a workspace's schema costs a few hundred milliseconds, and the
     * editor's terms read the same index in a later commit.
     */
    @Bean
    public SchemaIndexCache schemaIndexCache(DatabasePort databasePort) {
        return new SchemaIndexCache(databasePort);
    }

    @Bean
    public ShapesValidationUseCase shapesValidationUseCase(
            DatabasePort databasePort, SchemaIndexCache schemaIndexCache) {
        return new ShapesValidationService(databasePort, schemaIndexCache);
    }
}
