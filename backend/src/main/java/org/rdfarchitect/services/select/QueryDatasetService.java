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

package org.rdfarchitect.services.select;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.models.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryDatasetService implements GetDatasetSchemaUseCase, ListGraphsUseCase, ListPrefixesUseCase, ListDatasetsUseCase {

    private final DatabasePort databasePort;

    @Override
    public ByteArrayOutputStream getDatasetSchema(String datasetName, RDFFormat format) {
        var graphUris = databasePort.listGraphUris(datasetName);
        var resultDataset = DatasetFactory.create();

        //fetch graphs and insert into resultDataset
        for (String graphUri : graphUris) {
            if (graphUri.equals("default")) {
                resultDataset.setDefaultModel(getGraphAsModel(datasetName, "default"));
            } else {
                resultDataset.addNamedModel(graphUri, getGraphAsModel(datasetName, graphUri));
            }
        }

        //add DB prefixes too resultDataset
        resultDataset.getPrefixMapping().setNsPrefixes(databasePort.getPrefixMapping(datasetName));

        //format to file
        var outStream = new ByteArrayOutputStream();
        RDFDataMgr.write(outStream, resultDataset, format);

        return outStream;
    }

    private Model getGraphAsModel(String datasetName, String graphURI) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(new GraphIdentifier(datasetName, graphURI)).getRdfGraph();
            graph.begin(TxnType.READ);
            return ModelFactory.createModelForGraph(GraphUtils.deepCopy(graph));
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    @Override
    public List<URI> listGraphs(String datasetName) {
        List<String> graphUriList = databasePort.listGraphUris(datasetName);

        return graphUriList
                  .stream()
                  .map(URI::new)
                  .toList();
    }

    @Override
    public List<CIMPrefixPair> listPrefixes(String datasetName) {
        var prefixMapping = databasePort.getPrefixMapping(datasetName);

        var result = new ArrayList<CIMPrefixPair>();
        for (var prefix : prefixMapping.getNsPrefixMap().entrySet()) {
            result.add(new CIMPrefixPair(prefix.getKey() + ":", prefix.getValue()));
        }
        return result;
    }

    @Override
    public String listFormattedPrefixes(String datasetName, String format){
        var prefixMapping = databasePort.getPrefixMapping(datasetName);
        var model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(prefixMapping);
        var stream = new ByteArrayOutputStream();
        var lang = switch (format) {
            case "turtle", "ttl" -> Lang.TURTLE;
            case "n3" -> Lang.N3;
            case "nquads" -> Lang.NQUADS;
            case "nt" -> Lang.NT;
            case "trig" -> Lang.TRIG;
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
        RDFDataMgr.write(stream, model, lang);
        return stream.toString();
    }

    @Override
    public List<String> listDatasets() {
        return databasePort.listDatasets();
    }
}
