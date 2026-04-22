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

package org.rdfarchitect.cim.rendering.mermaid;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMPackageToMermaidBuilder;

import java.util.List;

class CIMPackageToMermaidBuilderTest {

    private static final String URI_PREFIX = "http://example.com#";

    private static final String PACKAGE_LABEL = "examplePackage";

    private static final String EXPECTED_PACKAGE_HEADER = "    namespace " + PACKAGE_LABEL + "{\n";

    private CIMPackage cimPackage;

    @BeforeEach
    void setUp() {
        cimPackage =
                CIMPackage.builder()
                        .uri(new URI(URI_PREFIX + PACKAGE_LABEL))
                        .label(new RDFSLabel(PACKAGE_LABEL))
                        .build();
    }

    @Test
    void build_emptyPackage_returnsEmptyString() {
        // Arrange
        var builder = new CIMPackageToMermaidBuilder(cimPackage, List.of());

        // Act
        var result = builder.build().toString();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void build_packageWithOneClass_returnsMermaidClassWrappedInMermaidNamespace() {
        // Arrange
        var cimClassStringBuilder =
                new StringBuilder("class `uuid`[\"label\"]{\n    <<abstract>>\n}\n");
        var builder = new CIMPackageToMermaidBuilder(cimPackage, List.of(cimClassStringBuilder));

        // Act
        var result = builder.build().toString();

        // Assert
        assertThat(result)
                .isEqualTo(
                        EXPECTED_PACKAGE_HEADER
                                + """
                                    class `uuid`["label"]{
                                        <<abstract>>
                                    }
                                }
                            """);
    }

    @Test
    void build_virtualPackage_returnsClassContents() {
        // Arrange
        var cimClassStringBuilder =
                new StringBuilder("class `uuid`[\"label\"]{\n    <<abstract>>\n}\n");
        var builder = new CIMPackageToMermaidBuilder(null, List.of(cimClassStringBuilder));

        // Act
        var result = builder.build().toString();

        // Assert
        assertThat(result)
                .isEqualTo(
                        """
                                                   class `uuid`["label"]{
                                                       <<abstract>>
                                                   }
                                               """);
    }
}
