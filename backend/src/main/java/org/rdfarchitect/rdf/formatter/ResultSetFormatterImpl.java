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

package org.rdfarchitect.rdf.formatter;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultSetFormatterImpl implements ResultFormatter {

    private static final Logger logger = LoggerFactory.getLogger(ResultSetFormatterImpl.class);

    ResultSet resultSet;

    public ResultSetFormatterImpl(ResultSet result) {
        this.resultSet = result;
    }

    @Override
    public ResultSet asResultSet() {
        ResultSet resultset = this.resultSet;
        logger.debug("Format result: ResultSet\n" + "Content: {}", resultset);
        return resultset;
    }

    /**
     * Parses RDF-triples from a {@link ResultSet} to a {@link Graph}. This implementation assumes
     * that the first three variables in the result set are the subject, predicate and object of the
     * triple.
     *
     * @return {@link Graph}
     */
    @Override
    public Graph asGraph() {
        var vars = resultSet.getResultVars();
        if (vars.size() < 3) {
            throw new IllegalArgumentException(
                    "Failed to parse triples from result set. "
                            + "Need at least three variables containing subject, predicate and object, got "
                            + vars.size());
        }
        var graph = GraphFactory.createDefaultGraph();
        while (resultSet.hasNext()) {
            QuerySolution qs = resultSet.next();
            Node s =
                    qs.get(vars.get(0))
                            .asNode(); // we can only process queries with Sub pre obj as result
            Node p = qs.get(vars.get(1)).asNode();
            Node o = qs.get(vars.get(2)).asNode();
            graph.add(Triple.create(s, p, o));
        }
        logger.debug("Format result: graph\n" + "Instance: {}", graph);
        return graph;
    }

    @Override
    public String asText() {
        String text = ResultSetFormatter.asText(resultSet);
        logger.debug("Format result: text\n" + "Content: {}", text);
        return text;
    }

    @Override
    public Model asModel() {
        Model model = ModelFactory.createModelForGraph(this.asGraph());
        logger.debug("Format result: model\n" + "Instance: {}", model);
        return model;
    }
}
