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

package org.rdfarchitect.services.update.classes;

import lombok.experimental.UtilityClass;

import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class UniqueLabelFactory {

    public RDFSLabel uniqueClassLabel(Graph graph, RDFSLabel label) {
        return uniqueLabel(label, existingClassLabels(graph, label));
    }

    public Set<String> existingAssociationLabels(Graph graph, URI domainUri, RDFSLabel label) {
        return queryLabels(
                graph,
                labelsStartingWith(label)
                        .addWhere("?s", RDFS.domain, NodeFactory.createURI(domainUri.toString())));
    }

    public RDFSLabel uniqueLabel(RDFSLabel label, Set<String> existingLabels) {
        var baseValue = label.getValue();
        if (!existingLabels.contains(baseValue)) {
            return new RDFSLabel(baseValue, label.getLang());
        }
        baseValue = label.getValue() + "-Copy";
        if (!existingLabels.contains(baseValue)) {
            return new RDFSLabel(baseValue, label.getLang());
        }
        var counter = 1;
        while (existingLabels.contains(baseValue + "(" + counter + ")")) {
            counter++;
        }
        return new RDFSLabel(baseValue + "(" + counter + ")", label.getLang());
    }

    private Set<String> existingClassLabels(Graph graph, RDFSLabel label) {
        return queryLabels(graph, labelsStartingWith(label).addWhere("?s", RDF.type, RDFS.Class));
    }

    private SelectBuilder labelsStartingWith(RDFSLabel label) {
        var exprFactory = new ExprFactory();
        return new SelectBuilder()
                .addVar("?label")
                .addWhere("?s", RDFS.label, "?label")
                .addFilter(exprFactory.strstarts(exprFactory.str("?label"), label.getValue()));
    }

    private Set<String> queryLabels(Graph graph, SelectBuilder builder) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        try (var queryExecution = QueryExecutionFactory.create(builder.build(), dataset)) {
            var resultSet = ResultSetFactory.copyResults(queryExecution.execSelect());
            var existingLabels = new HashSet<String>();
            while (resultSet.hasNext()) {
                existingLabels.add(resultSet.nextSolution().getLiteral("label").getString());
            }
            return existingLabels;
        }
    }
}
