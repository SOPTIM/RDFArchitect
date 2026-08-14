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

package org.rdfarchitect.services.rendering;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.select.ListGraphsUseCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CIMProfileModels {

    private CIMProfileModels() {}

    /**
     * Loads every graph of the dataset as a {@link CIMProfileModel}, optionally skipping one graph.
     *
     * @param excludedGraphUri graph to leave out, or null to load all of them
     */
    public static List<CIMProfileModel> loadAll(
            DatabasePort databasePort,
            Map<String, String> keywordsByGraphUri,
            String datasetName,
            String excludedGraphUri) {
        var profiles = new ArrayList<CIMProfileModel>();
        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            if (graphUri.equals(excludedGraphUri)) {
                continue;
            }
            profiles.add(
                    load(databasePort, datasetName, graphUri, keywordsByGraphUri.get(graphUri)));
        }
        return profiles;
    }

    /** Maps each graph of the dataset to its {@code dcat:keyword} short name. */
    public static Map<String, String> keywordsByGraphUri(
            ListGraphsUseCase listGraphsUseCase, String datasetName) {
        var keywords = new HashMap<String, String>();
        for (var graph : listGraphsUseCase.listGraphs(datasetName)) {
            if (graph.getUri() != null) {
                keywords.put(graph.getUri().toString(), graph.getKeyword());
            }
        }
        return keywords;
    }

    /**
     * Loads a graph of the dataset as a {@link CIMProfileModel}, reading it through the CIM facade
     * and tagging it with its cross-profile color and short name.
     */
    private static CIMProfileModel load(
            DatabasePort databasePort, String datasetName, String graphUri, String keyword) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        Graph graphCopy;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            graphCopy = GraphUtils.deepCopy(ctx.getRdfGraph());
        }

        var color = databasePort.getCrossProfileDiagramInfo(datasetName).getColor(graphUri);
        var model = new CIMModelFacade(graphUri, ModelFactory.createModelForGraph(graphCopy));
        return new CIMProfileModel(graphUri, color, keyword, model);
    }
}
