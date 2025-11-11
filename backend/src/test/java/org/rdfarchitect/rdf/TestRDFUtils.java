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

package org.rdfarchitect.rdf;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;

import static org.assertj.core.api.Assertions.*;

public class TestRDFUtils {

    public static Triple triple(String triple) {
        var split = triple.split("\\s");
        assertThat(split).hasSize(3);
        return Triple.create(
                  NodeFactory.createURI(split[0]),
                  NodeFactory.createURI(split[1]),
                  NodeFactory.createURI(split[2]));
    }
}
