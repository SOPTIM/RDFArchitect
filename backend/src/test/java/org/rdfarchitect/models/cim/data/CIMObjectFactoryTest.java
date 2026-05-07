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

package org.rdfarchitect.models.cim.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;

import java.util.UUID;

class CIMObjectFactoryTest {

    @Test
    void createExternalCIMPackageList_preservesUriSuffixAsLabel() {
        var packageUuid = UUID.randomUUID();
        var querySolution = new QuerySolutionMap();
        querySolution.add(
                CIMQueryVars.URI,
                ResourceFactory.createResource("http://example.org#Package_TestPackage"));
        querySolution.add(
                CIMQueryVars.UUID, ResourceFactory.createStringLiteral(packageUuid.toString()));

        var resultSet = mock(ResultSet.class);
        when(resultSet.hasNext()).thenReturn(true, false);
        when(resultSet.next()).thenReturn(querySolution);

        var packages = CIMObjectFactory.createExternalCIMPackageList(resultSet);

        assertThat(packages).hasSize(1);
        assertThat(packages.getFirst().getUri().toString())
                .isEqualTo("http://example.org#Package_TestPackage");
        assertThat(packages.getFirst().getLabel().getValue()).isEqualTo("Package_TestPackage");
        assertThat(packages.getFirst().getUuid()).isEqualTo(packageUuid);
    }
}
