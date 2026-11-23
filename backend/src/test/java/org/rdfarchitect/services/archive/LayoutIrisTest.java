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

package org.rdfarchitect.services.archive;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

class LayoutIrisTest {

    private static final String CIM = "http://iec.ch/TC57/CIM100#";

    @Test
    void archivedLayout_survivesBeingWrittenAndParsedWithABase() {
        var layout = layout();
        var out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, LayoutIris.toArchived(layout), Lang.TURTLE);

        var parsed =
                RDFParser.fromString(out.toString(StandardCharsets.UTF_8), Lang.TURTLE)
                        .base("file:///somewhere/else/")
                        .toGraph();

        assertThat(LayoutIris.fromArchived(parsed).isIsomorphicWith(layout)).isTrue();
    }

    private static Graph layout() {
        var layout = GraphFactory.createDefaultGraph();
        var object = NodeFactory.createURI("_object");
        layout.add(
                Triple.create(
                        object,
                        NodeFactory.createURI(CIM + "DiagramObject.Diagram"),
                        NodeFactory.createURI("_diagram")));
        layout.add(
                Triple.create(
                        object,
                        NodeFactory.createURI(CIM + "IdentifiedObject.name"),
                        NodeFactory.createLiteralString("_not-an-iri")));
        layout.add(
                Triple.create(
                        NodeFactory.createBlankNode(),
                        NodeFactory.createURI(CIM + "DiagramObjectPoint.DiagramObject"),
                        object));
        return layout;
    }
}
