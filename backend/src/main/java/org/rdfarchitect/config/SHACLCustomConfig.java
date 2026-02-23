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
import org.rdfarchitect.services.shacl.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SHACLCustomConfig {

    @Bean
    public SHACLInsertUseCase shaclInsertUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLGetClassRelationsUseCase shaclGetClassRelationsUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLGetShapeUseCase shaclGetShapeUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLReplaceShapeUseCase shaclReplaceShapeUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLExportUseCase shaclExportUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLDeleteShapeUseCase shaclDeleteShapeUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }

    @Bean
    public SHACLUpdateUseCase shaclUpdateUseCase(DatabasePort databasePort) {
        return new SingletonPrimitiveSHACLStoringService(databasePort);
    }
}
