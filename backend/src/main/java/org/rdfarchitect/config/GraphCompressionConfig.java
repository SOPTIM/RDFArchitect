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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphCompressionConfig {

    private static final int DEFAULT_MAX_VERSIONS = 20;
    private static final int DEFAULT_COMPRESS_COUNT = 5;

    @Getter private static int maxVersions = DEFAULT_MAX_VERSIONS;
    @Getter private static int compressCount = DEFAULT_COMPRESS_COUNT;

    @Value("${graph.maxVersions:" + DEFAULT_MAX_VERSIONS + "}")
    @SuppressWarnings("java:S2696")
    public void setMaxVersions(int maxVersions) {
        GraphCompressionConfig.maxVersions = maxVersions;
    }

    @Value("${graph.compressCount:" + DEFAULT_COMPRESS_COUNT + "}")
    @SuppressWarnings("java:S2696")
    public void setCompressCount(int compressCount) {
        GraphCompressionConfig.compressCount = compressCount;
    }
}
