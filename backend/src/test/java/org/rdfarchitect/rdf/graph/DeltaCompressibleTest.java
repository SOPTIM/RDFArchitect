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

package org.rdfarchitect.rdf.graph;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.Delta;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** only testing compress method, because the rest is copy pasted from the {@link Delta} */
class DeltaCompressibleTest {

    @Test
    void compress() {
        // set up
        Graph baseGraph = GraphFactory.createDefaultGraph();
        Graph compareGraph = GraphFactory.createDefaultGraph();

        // base
        List<Triple> base = new ArrayList<>();
        base.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("a")));
        base.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("b")));
        base.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("c")));
        for (Triple t : base) {
            baseGraph.add(t);
            compareGraph.add(t);
        }
        DeltaCompressible deltaCompressible = new DeltaCompressible(baseGraph);
        // additions
        List<Triple> additions = new ArrayList<>();
        additions.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("b"),
                        NodeFactory.createURI("a")));
        additions.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("b"),
                        NodeFactory.createURI("b")));
        additions.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("b"),
                        NodeFactory.createURI("c")));
        for (Triple t : additions) {
            deltaCompressible.add(t);
            compareGraph.add(t);
        }
        // deletions
        List<Triple> deletions = new ArrayList<>();
        deletions.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("c")));
        deletions.add(
                Triple.create(
                        NodeFactory.createURI("a"),
                        NodeFactory.createURI("b"),
                        NodeFactory.createURI("c")));
        for (Triple t : deletions) {
            deltaCompressible.delete(t);
            compareGraph.delete(t);
        }

        // assertions
        assertThat(deltaCompressible.isIsomorphicWith(compareGraph)).isTrue();
        assertThatNoException().isThrownBy(deltaCompressible::compress);
        assertThat(deltaCompressible.isIsomorphicWith(compareGraph)).isTrue();
        assertThat(deltaCompressible.getAdditions().find().toList()).isEmpty();
        assertThat(deltaCompressible.getDeletions().find().toList()).isEmpty();
    }
}
