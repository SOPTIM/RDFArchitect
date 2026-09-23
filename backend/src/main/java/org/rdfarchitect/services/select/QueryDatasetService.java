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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.DatasetDTO;
import org.rdfarchitect.api.dto.GraphDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMPrefixPair;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryDatasetService
        implements GetDatasetSchemaUseCase,
                ListGraphsUseCase,
                ListPrefixesUseCase,
                ListDatasetsUseCase {

    /** How a CGMES 2.4.15 profile names the class its fixed profile properties hang off. */
    private static final String PROFILE_VERSION_SUFFIX = "Version";

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

        // Read once for the whole listing: it cannot change while it is running, and asking the
        // store per graph takes its lock once per profile of the workspace.
        var datasetPrefixes = databasePort.getPrefixMapping(datasetName);
        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            result.add(readGraph(datasetName, graphUri, datasetPrefixes));
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
    private GraphDTO readGraph(String datasetName, String graphUri, PrefixMapping datasetPrefixes) {
        var dto = GraphDTO.builder().uri(new URI(graphUri)).build();
        try (var ctx =
                databasePort
                        .getGraphWithContext(new GraphIdentifier(datasetName, graphUri))
                        .begin(ReadWrite.READ)) {
            var stored = ctx.getRdfGraph();
            var graph = GraphUtils.deepCopy(stored);
            var prefixes = graph.getPrefixMapping().setNsPrefixes(stored.getPrefixMapping());
            fillInMissingPrefixes(prefixes, datasetPrefixes);
            var profile = CimProfile.wrap(graph);
            var metadata = profile.getMetadata();
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
            dto.setOntologyHeader(profile.getOntologyNode() != null);
            if (profile.getOntologyNode() == null) {
                var versionClass = versionClassOf(graph);
                dto.setProfileClassIri(versionClass == null ? null : versionClass.getURI());
                dto.setProfileClassUuid(uuidOf(graph, versionClass));
            }
        } catch (IllegalArgumentException e) {
            // Not a CIM profile, so there is no profile metadata to report.
        }
        return dto;
    }

    /**
     * Adds the dataset's bindings for the prefixes the graph does not spell out itself.
     *
     * <p>Only the prefix decides, which Jena's {@code withDefaultMappings} does not do: it also
     * withholds a binding whose namespace is already bound under some other prefix. For {@code cim}
     * that is the one binding a profile is read by, so a graph spelling the CIM namespace {@code
     * cim16} would be left without a {@code cim} prefix and stop being a CIM profile at all.
     */
    private static void fillInMissingPrefixes(
            PrefixMapping prefixes, PrefixMapping datasetPrefixes) {
        datasetPrefixes
                .getNsPrefixMap()
                .forEach(
                        (prefix, namespace) -> {
                            if (prefixes.getNsPrefixURI(prefix) == null) {
                                prefixes.setNsPrefix(prefix, namespace);
                            }
                        });
    }

    /**
     * The class a CGMES 2.4.15 profile keeps its keyword and version IRIs on, as fixed values of
     * its properties. There is no ontology object to edit in such a profile, so this class is where
     * a reader has to be sent instead.
     *
     * <p>Found the way cimxml finds it — the {@code rdfs:domain} whose name ends in "Version" —
     * because cimxml keeps that lookup package-private. Unlike cimxml this takes the lowest URI
     * rather than the first match: a graph is an unordered set of triples, and a merged or
     * hand-extended profile can hold more than one such class, so the class editor would otherwise
     * open whichever one the iterator happened to yield.
     */
    private static Node versionClassOf(Graph graph) {
        return graph.stream(Node.ANY, RDFS.domain.asNode(), Node.ANY)
                .map(Triple::getObject)
                .filter(Node::isURI)
                .filter(node -> node.getURI().endsWith(PROFILE_VERSION_SUFFIX))
                .min(Comparator.comparing(Node::getURI))
                .orElse(null);
    }

    /** The identity the class editor navigates by, which is the uuid rather than the IRI. */
    private static String uuidOf(Graph graph, Node resource) {
        if (resource == null) {
            return null;
        }
        return graph.stream(resource, RDFA.uuid.asNode(), Node.ANY)
                .map(Triple::getObject)
                .filter(Node::isLiteral)
                .map(Node::getLiteralLexicalForm)
                .findFirst()
                .orElse(null);
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
