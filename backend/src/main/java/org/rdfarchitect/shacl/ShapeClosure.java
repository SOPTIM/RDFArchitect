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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACL;

import java.util.ArrayDeque;
import java.util.HashSet;

/** What a shape is made of: its own statements and those of everything it holds inline. */
public final class ShapeClosure {

    private ShapeClosure() {}

    /**
     * Copies {@code subject}'s statements to {@code target}, following blank nodes and {@code
     * sh:sparql} constraints.
     *
     * <p>Walked with a work list and a visited set rather than by recursion. The shapes come from
     * documents users write and import: a cyclic RDF list sent the recursive copy round forever,
     * and a few hundred levels of nested {@code sh:or} overflowed the stack, so one malformed or
     * merely deep document failed the constraints dialog of every class it touched.
     */
    public static void copy(Model source, Model target, Resource subject) {
        var visited = new HashSet<RDFNode>();
        var pending = new ArrayDeque<Resource>();
        pending.push(subject);
        visited.add(subject);
        while (!pending.isEmpty()) {
            var current = pending.pop();
            var statements = source.listStatements(current, null, (RDFNode) null);
            while (statements.hasNext()) {
                var statement = statements.nextStatement();
                target.add(statement);
                var object = statement.getObject();
                var follow =
                        object.isAnon()
                                || (object.isResource()
                                        && SHACL.sparql.equals(statement.getPredicate().asNode()));
                if (follow && visited.add(object)) {
                    pending.push(object.asResource());
                }
            }
        }
    }
}
