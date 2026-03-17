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

package org.rdfarchitect.services.update.ontology;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.cim.changelog.ChangeLogEntry;
import org.rdfarchitect.cim.ontology.OntologyFacade;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateOntologyService implements CreateOntologyUseCase, UpdateOntologyUseCase, DeleteOntologyUseCase {

    private final DatabasePort databasePort;
    private final ExpandURIUseCase expandURIUseCase;
    private final ChangeLogUseCase changeLogUseCase;

    // CREATE
    @Override
    public void createOntology(GraphIdentifier graphIdentifier, OntologyDTO ontologyDTO) {
        expandOntologyIris(graphIdentifier.getDatasetName(), ontologyDTO);
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));

            if (ontologyDTO.getUuid() == null) {
                ontologyDTO.setUuid(UUID.randomUUID().toString());
            } else {
                if (isInvalidUUID(ontologyDTO.getUuid())) {
                    throw new IllegalArgumentException("Invalid UUID for ontology: " + ontologyDTO.getUuid());
                }
            }
            var ontology = new OntologyFacade(model);
            ontology.createOntology(ontologyDTO);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
                changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Created Ontology", graph.getLastDelta()));
            }
        }
    }

    // UPDATE
    @Override
    public void replaceOntology(GraphIdentifier graphIdentifier, OntologyDTO ontologyDTO) {
        expandOntologyIris(graphIdentifier.getDatasetName(), ontologyDTO);
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));

            if (ontologyDTO.getUuid() == null) {
                ontologyDTO.setUuid(UUID.randomUUID().toString());
            } else {
                if (isInvalidUUID(ontologyDTO.getUuid())) {
                    throw new IllegalArgumentException("Invalid UUID for ontology: " + ontologyDTO.getUuid());
                }
            }
            var ontology = new OntologyFacade(model);
            ontology.replaceOntology(ontologyDTO);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
                changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Replaced Ontology", graph.getLastDelta()));
            }
        }
    }

    // DELETE
    @Override
    public void deleteOntology(GraphIdentifier graphIdentifier) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));

            var ontology = new OntologyFacade(model);
            ontology.deleteOntology();
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
                changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Deleted Ontology", graph.getLastDelta()));
            }
        }
    }

    private void expandOntologyIris(String dataset, OntologyDTO ontologyDTO) {
        var ontologyNamespace = ontologyDTO.getNamespace();
        ontologyDTO.setNamespace(expandURIUseCase.expandUri(dataset, ontologyNamespace));

        var entries = ontologyDTO.getEntries();
        for (var entry : entries) {
            var entryNamespace = entry.getIri();
            entry.setIri(expandURIUseCase.expandUri(dataset, entryNamespace));
            if (entry.getDatatypeIri() != null) {
                entry.setDatatypeIri(expandURIUseCase.expandUri(dataset, entryNamespace));
            }
        }
    }

    private boolean isInvalidUUID(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return false;
        } catch (IllegalArgumentException _) {
            return true;
        }
    }
}
