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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.ontology.OntologyFacade;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateOntologyService
        implements CreateOntologyUseCase, UpdateOntologyUseCase, DeleteOntologyUseCase {

    private final DatabasePort databasePort;
    private final ExpandURIUseCase expandURIUseCase;

    // CREATE
    @Override
    public void createOntology(GraphIdentifier graphIdentifier, OntologyDTO ontologyDTO) {
        expandOntologyIris(graphIdentifier.datasetName(), ontologyDTO);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            if (ontologyDTO.getUuid() == null) {
                ontologyDTO.setUuid(UUID.randomUUID().toString());
            } else {
                if (isInvalidUUID(ontologyDTO.getUuid())) {
                    throw new IllegalArgumentException(
                            "Invalid UUID for ontology: " + ontologyDTO.getUuid());
                }
            }
            new OntologyFacade(model).createOntology(ontologyDTO);
            ctx.commit("Created Ontology");
        }
    }

    // UPDATE
    @Override
    public void replaceOntology(GraphIdentifier graphIdentifier, OntologyDTO ontologyDTO) {
        expandOntologyIris(graphIdentifier.datasetName(), ontologyDTO);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            if (ontologyDTO.getUuid() == null) {
                ontologyDTO.setUuid(UUID.randomUUID().toString());
            } else {
                if (isInvalidUUID(ontologyDTO.getUuid())) {
                    throw new IllegalArgumentException(
                            "Invalid UUID for ontology: " + ontologyDTO.getUuid());
                }
            }
            new OntologyFacade(model).replaceOntology(ontologyDTO);
            ctx.commit("Replaced Ontology");
        }
    }

    // DELETE
    @Override
    public void deleteOntology(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            new OntologyFacade(model).deleteOntology();
            ctx.commit("Deleted Ontology");
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
