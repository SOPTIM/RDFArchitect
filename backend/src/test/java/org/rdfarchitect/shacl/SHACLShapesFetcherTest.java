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

package org.rdfarchitect.shacl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SHACLShapesFetcherTest {

    private static final String SHAPE_URI = "http://ex.org/shapes#Line.length-datatype";

    private static SHACLShapesFetcher fetcherForShapeWithOrder(String order) {
        var turtle =
                """
                PREFIX sh:  <http://www.w3.org/ns/shacl#>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

                <%s> a sh:PropertyShape;
                    sh:path <http://ex.org/cim#Line.length>;
                    sh:order %s .
                """
                        .formatted(SHAPE_URI, order);
        return new SHACLShapesFetcher(
                RDFParser.fromString(turtle, Lang.TURTLE).checking(false).toModel());
    }

    @ParameterizedTest
    @CsvSource({"0.1, 0.1", "3, 3.0"})
    void getPropertyShape_numericOrder_returnsItsValue(String order, double expected) {
        var propertyShape = fetcherForShapeWithOrder(order).getPropertyShape(SHAPE_URI);

        assertThat(propertyShape.getOrder()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"first\"", "\"abc\"^^xsd:decimal", "<http://ex.org/order>"})
    void getPropertyShape_invalidOrder_returnsZero(String order) {
        var propertyShape = fetcherForShapeWithOrder(order).getPropertyShape(SHAPE_URI);

        assertThat(propertyShape.getOrder()).isZero();
    }
}
