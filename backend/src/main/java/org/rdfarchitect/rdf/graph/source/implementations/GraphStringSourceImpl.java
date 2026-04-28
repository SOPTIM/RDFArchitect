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

package org.rdfarchitect.rdf.graph.source.implementations;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.rdfarchitect.rdf.graph.source.GraphSource;

import java.io.StringReader;

public class GraphStringSourceImpl implements GraphSource {

    private static final String GRAPH_SOURCE_TYPE = "String";

    private final String graphString;

    private final String graphName;

    public GraphStringSourceImpl(String graphString) {
        this.graphString = graphString;
        this.graphName = "unnamed";
    }

    public GraphStringSourceImpl(String graphString, String graphName) {
        this.graphString = graphString;
        this.graphName = graphName;
    }

    @Override
    public Graph graph() {
        var model = ModelFactory.createDefaultModel();
        try (StringReader reader = new StringReader(graphString)) {
            model.read(reader, null, Lang.TURTLE.getName());
        }
        return model.getGraph();
    }

    @Override
    public String graphName() {
        return graphName;
    }

    @Override
    public String getGraphSourceType() {
        return GRAPH_SOURCE_TYPE;
    }
}
