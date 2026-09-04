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

import de.soptim.opencgmes.cimxml.graph.CimProfile;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Node;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.api.dto.DatasetDTO;
import org.rdfarchitect.api.dto.GraphDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryDatasetService
        implements GetDatasetSchemaUseCase,
                ListGraphsUseCase,
                ListPrefixesUseCase,
                ListDatasetsUseCase {

    private final DatabasePort databasePort;

    @Override
    public ByteArrayOutputStream getDatasetSchema(String datasetName, RDFFormat format) {
        var graphUris = databasePort.listGraphUris(datasetName);
        var resultDataset = DatasetFactory.create();

        // fetch graphs and insert into resultDataset
        for (String graphUri : graphUris) {
            if (graphUri.equals("default")) {
                resultDataset.setDefaultModel(getGraphAsModel(datasetName, "default"));
            } else {
                resultDataset.addNamedModel(graphUri, getGraphAsModel(datasetName, graphUri));
            }
        }

        // add DB prefixes too resultDataset
        resultDataset.getPrefixMapping().setNsPrefixes(databasePort.getPrefixMapping(datasetName));

        // format to file
        var outStream = new ByteArrayOutputStream();
        RDFDataMgr.write(outStream, resultDataset, format);

        return outStream;
    }

    private Model getGraphAsModel(String datasetName, String graphURI) {
        try (var ctx =
                databasePort
                        .getGraphWithContext(new GraphIdentifier(datasetName, graphURI))
                        .begin(ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(GraphUtils.deepCopy(ctx.getRdfGraph()));
        }
    }

    @Override
    public List<GraphDTO> listGraphs(String datasetName) {
        var result = new ArrayList<GraphDTO>();

        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            result.add(readGraph(datasetName, graphUri));
        }

        return result;
    }

    /**
     * A graph as the schema pickers name it: its URI, plus everything the CIM profile in it says
     * about itself. A graph that is not a CIM profile is still listed, by its URI alone.
     *
     * <p>The profile is read in a single pass, since the graph has to be copied out of the store to
     * be read at all and this runs once per graph of the dataset.
     *
     * <p>Which CIM version a profile is written in is decided by the namespace bound to its {@code
     * cim} prefix, and a dataset holding both CGMES 2.4.15 and CGMES 3.0 schemas can only remember
     * one of them. The graph's own prefixes therefore win, and the dataset's fill in only what the
     * graph does not declare itself.
     */
    private GraphDTO readGraph(String datasetName, String graphUri) {
        var dto = GraphDTO.builder().uri(new URI(graphUri)).build();
        try (var ctx =
                databasePort
                        .getGraphWithContext(new GraphIdentifier(datasetName, graphUri))
                        .begin(ReadWrite.READ)) {
            var stored = ctx.getRdfGraph();
            var graph = GraphUtils.deepCopy(stored);
            graph.getPrefixMapping()
                    .setNsPrefixes(stored.getPrefixMapping())
                    .withDefaultMappings(databasePort.getPrefixMapping(datasetName));
            var metadata = CimProfile.wrap(graph).getMetadata();
            dto.setKeyword(metadata.keyword());
            dto.setLabel(metadata.label());
            dto.setDescription(metadata.description());
            dto.setVersionInfo(metadata.versionInfo());
            dto.setVersionIris(
                    metadata.versionIris().stream()
                            .filter(Node::isURI)
                            .map(Node::getURI)
                            .sorted()
                            .toList());
        } catch (IllegalArgumentException e) {
            // Not a CIM profile, so there is no profile metadata to report.
        }
        return dto;
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
    public String listFormattedPrefixes(String datasetName, String format) {
        var prefixMapping = databasePort.getPrefixMapping(datasetName);
        var model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(prefixMapping);
        var stream = new ByteArrayOutputStream();
        var lang =
                switch (format) {
                    case "turtle", "ttl" -> Lang.TURTLE;
                    case "n3" -> Lang.N3;
                    case "nquads" -> Lang.NQUADS;
                    case "nt" -> Lang.NT;
                    case "trig" -> Lang.TRIG;
                    default -> throw new IllegalArgumentException("Unsupported format: " + format);
                };
        RDFDataMgr.write(stream, model, lang);
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public List<DatasetDTO> listDatasets() {
        var result = new ArrayList<DatasetDTO>();
        for (var datasetName : databasePort.listDatasets()) {
            var readonly = databasePort.isReadOnly(datasetName);
            var prefixMapping = databasePort.getPrefixMapping(datasetName);

            var prefixPairs = new ArrayList<CIMPrefixPair>();
            for (var prefix : prefixMapping.getNsPrefixMap().entrySet()) {
                prefixPairs.add(new CIMPrefixPair(prefix.getKey() + ":", prefix.getValue()));
            }
            result.add(new DatasetDTO(datasetName, readonly, prefixPairs));
        }
        return result;
    }
}
