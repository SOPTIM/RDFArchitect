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

package org.rdfarchitect.models.cim;

import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CIMQuerySolutionParserTest {

    @Test
    void getBelongsToCategory_withoutExplicitLabel_preservesUriSuffix() {
        var packageUuid = UUID.randomUUID();
        var querySolution = new QuerySolutionMap();
        querySolution.add(CIMQueryVars.PACKAGE_URI, ResourceFactory.createResource("http://example.org#Package_TestPackage"));
        querySolution.add(CIMQueryVars.PACKAGE_UUID, ResourceFactory.createStringLiteral(packageUuid.toString()));

        var belongsToCategory = new CIMQuerySolutionParser(querySolution).getBelongsToCategory(
                  CIMQueryVars.PACKAGE_URI,
                  CIMQueryVars.PACKAGE_LABEL,
                  CIMQueryVars.PACKAGE_UUID
        );

        assertThat(belongsToCategory.getUri().toString()).isEqualTo("http://example.org#Package_TestPackage");
        assertThat(belongsToCategory.getLabel().getValue()).isEqualTo("Package_TestPackage");
        assertThat(belongsToCategory.getUuid()).isEqualTo(packageUuid);
    }
}
