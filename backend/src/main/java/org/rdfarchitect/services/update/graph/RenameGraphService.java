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

package org.rdfarchitect.services.update.graph;

import de.soptim.opencgmes.cimxml.graph.CimProfile;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.ontology.OntologyFacade;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RenameGraphService implements RenameGraphUseCase {

    private static final String DCTERMS_TITLE = "http://purl.org/dc/terms/title";

    private final DatabasePort databasePort;

    @Override
    public void renameGraph(GraphIdentifier graphIdentifier, String newGraphUri, String newName) {
        databasePort.renameGraph(graphIdentifier, newGraphUri);
        var renamedIdentifier = new GraphIdentifier(graphIdentifier.datasetName(), newGraphUri);
        var nameUpdated = false;
        try {
            updateName(renamedIdentifier, newName);
            nameUpdated = true;
        } finally {
            if (!nameUpdated) {
                databasePort.renameGraph(renamedIdentifier, graphIdentifier.graphUri());
            }
        }
    }

    /**
     * Writes the name where the schema is read from, which differs by CIM version: a profile with
     * an ontology object keeps it in {@code dcterms:title}, a CGMES 2.4.15 profile on the package
     * that stands for the profile itself. A graph that is neither is left untouched — nothing in it
     * would be read back, and the tail of its URI is what names it.
     */
    private void updateName(GraphIdentifier graphIdentifier, String newName) {
        if (newName == null) {
            return;
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var ontologyFacade = new OntologyFacade(model);
            var ontology = ontologyFacade.getOntology();
            if (ontology != null) {
                applyTitle(ontology, newName);
                ontologyFacade.replaceOntology(ontology);
            } else if (!applyPackageLabel(graph, newName)) {
                return;
            }
            ctx.commit("Renamed schema to " + newName);
        }
    }

    private void applyTitle(OntologyDTO ontology, String newName) {
        var existingEntry =
                ontology.getEntries().stream()
                        .filter(entry -> DCTERMS_TITLE.equals(entry.getIri()))
                        .findFirst();
        if (existingEntry.isPresent()) {
            existingEntry.get().setValue(newName);
            return;
        }
        ontology.getEntries().add(new OntologyEntry().setIri(DCTERMS_TITLE).setValue(newName));
    }

    /**
     * Names a CGMES 2.4.15 profile on the package that stands for the profile itself, which is
     * where {@code CimProfile.getLabel} reads it back from.
     *
     * <p>The graph has to be read through cimxml to find that package, and cimxml rejects anything
     * that is not a profile — that rejection is the "no name to write" case, not a failure.
     *
     * @return whether a name was written
     */
    private boolean applyPackageLabel(Graph graph, String newName) {
        var profilePackage = profilePackageOf(graph);
        if (profilePackage == null) {
            return false;
        }
        // The official profiles tag their labels "@en"; keep whatever tag the one being
        // replaced carried rather than dropping it on a rename.
        var languageTag =
                graph.stream(profilePackage, RDFS.label.asNode(), Node.ANY)
                        .map(Triple::getObject)
                        .filter(Node::isLiteral)
                        .map(Node::getLiteralLanguage)
                        .filter(tag -> !tag.isEmpty())
                        .findFirst()
                        .orElse(null);
        graph.remove(profilePackage, RDFS.label.asNode(), Node.ANY);
        graph.add(profilePackage, RDFS.label.asNode(), label(newName, languageTag));
        return true;
    }

    private static Node label(String newName, String languageTag) {
        return languageTag == null
                ? NodeFactory.createLiteralString(newName)
                : NodeFactory.createLiteralLang(newName, languageTag);
    }

    private Node profilePackageOf(Graph graph) {
        // cimxml decides the CIM version from the graph's own prefixes, which deepCopy drops.
        var readable = GraphUtils.deepCopy(graph);
        readable.getPrefixMapping().setNsPrefixes(graph.getPrefixMapping());
        try {
            return CimProfile.wrap(readable).getProfilePackage();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
