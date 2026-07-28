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

public final class CIMProfileModels {

    private CIMProfileModels() {}

    /**
     * Loads a graph of the dataset as a {@link CIMProfileModel}, reading it through the CIM facade
     * and tagging it with its cross-profile color.
     */
    public static CIMProfileModel load(
            DatabasePort databasePort, String datasetName, String graphUri) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        Graph graphCopy;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            graphCopy = GraphUtils.deepCopy(ctx.getRdfGraph());
        }

        var color = databasePort.getCrossProfileDiagramInfo(datasetName).getColor(graphUri);
        var model = new CIMModelFacade(graphUri, ModelFactory.createModelForGraph(graphCopy));
        return new CIMProfileModel(graphUri, color, model);
    }
}
