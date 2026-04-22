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

import lombok.Getter;
import lombok.Setter;

import org.apache.jena.riot.Lang;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.implementations.file.FileConnectionImpl;
import org.rdfarchitect.database.implementations.http.FusekiHttpAdminProtocol;
import org.rdfarchitect.database.implementations.http.HttpConnectionImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Parses the database config and provides access to its contents */
@Getter
@Setter
@Configuration
public class DatabaseConfig {

    @Value("${database.databaseType:file}")
    private String databaseType;

    @Value("${database.defaultDataset:default}")
    private String defaultDataset;

    // http
    @Value("${database.http.endpoint}")
    private String httpEndpoint;

    // file
    @Value("${database.file.endpoint:C:/fileDatabase}")
    private String fileEndpoint;

    @Value("${database.file.dataType:trig}")
    private String dataType;

    @Bean
    public DatabaseConnection databaseConnection() {
        if ("http".equals(databaseType)) {
            return new HttpConnectionImpl(
                    httpEndpoint, defaultDataset, new FusekiHttpAdminProtocol(httpEndpoint));
        }
        return new FileConnectionImpl(
                fileEndpoint,
                defaultDataset,
                getFileLang(dataType)); // all options must return a DatabaseConnection even if the
        // option doesn't happen.
    }

    private static Lang getFileLang(String dataType) {
        dataType = dataType.toUpperCase();
        if ("N-QUADS".equals(dataType)) {
            return Lang.NQUADS;
        }
        return Lang.TRIG;
    }
}
