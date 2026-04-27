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

package org.rdfarchitect.rdf.graph.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A Graph wrapper that returns sorted triples when queried. Note: The triples are only sorted when
 * queried, the internal storage is not sorted. This means that the performance of add and delete
 * operations is not affected. However, the performance of find operations may be affected due to
 * the sorting step.
 */
@RequiredArgsConstructor
public class SortedGraph implements Graph {

    private final Comparator<Triple> tripleComparator;

    @Delegate private final Graph graph;

    public SortedGraph(Comparator<Triple> tripleComparator) {
        this.graph = GraphFactory.createDefaultGraph();
        this.tripleComparator = tripleComparator;
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        var list = this.graph.find(m).toList();
        list.sort(tripleComparator);
        return WrappedIterator.create(list.iterator());
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        var list = this.graph.find(s, p, o).toList();
        list.sort(tripleComparator);
        return WrappedIterator.create(list.iterator());
    }

    @Override
    public Stream<Triple> stream(Node s, Node p, Node o) {
        return Iter.asStream(this.find(s, p, o));
    }

    @Override
    public Stream<Triple> stream() {
        return this.stream(Node.ANY, Node.ANY, Node.ANY);
    }

    @Override
    public ExtendedIterator<Triple> find() {
        return this.find(Node.ANY, Node.ANY, Node.ANY);
    }
}
