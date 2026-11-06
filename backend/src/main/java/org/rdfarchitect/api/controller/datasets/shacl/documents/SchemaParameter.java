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

package org.rdfarchitect.api.controller.datasets.shacl.documents;

import org.rdfarchitect.services.ExpandURIUseCase;

import java.util.List;
import java.util.Objects;

/** The schemas a request names, which a query parameter may give as prefixed names. */
final class SchemaParameter {

    private SchemaParameter() {}

    /**
     * @param schemas the values as sent, or {@code null} when none were
     * @return their full URIs, leaving out what an empty {@code schema=} arrives as
     */
    static List<String> expanded(
            List<String> schemas, String datasetName, ExpandURIUseCase expandURIUseCase) {
        if (schemas == null) {
            return List.of();
        }
        return schemas.stream()
                .filter(Objects::nonNull)
                .filter(schema -> !schema.isBlank())
                .map(schema -> expandURIUseCase.expandUri(datasetName, schema))
                .toList();
    }
}
