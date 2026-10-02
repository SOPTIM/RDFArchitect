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

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.DCAT;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.models.cim.ontology.OntologyFacade;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RenameGraphService implements RenameGraphUseCase {

    private final DatabasePort databasePort;

    @Override
    public void renameGraph(
            GraphIdentifier graphIdentifier, String newGraphUri, String newKeyword) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            transaction.renameGraph(graphIdentifier.graphUri(), newGraphUri);
            updateKeyword(transaction, graphIdentifier.datasetName(), newGraphUri, newKeyword);
            transaction.commit("renamed schema to " + newGraphUri);
        }
    }

    private void updateKeyword(
            WorkspaceTransaction transaction,
            String workspaceName,
            String graphUri,
            String newKeyword) {
        if (newKeyword == null) {
            return;
        }
        var model = ModelFactory.createModelForGraph(transaction.graph(graphUri).getRdfGraph());
        model.setNsPrefixes(databasePort.getPrefixMapping(workspaceName));
        var ontologyFacade = new OntologyFacade(model);
        var ontology = ontologyFacade.getOntology();
        if (ontology == null) {
            return;
        }
        applyKeyword(ontology, newKeyword);
        ontologyFacade.replaceOntology(ontology);
    }

    private void applyKeyword(OntologyDTO ontology, String newKeyword) {
        var existingEntry =
                ontology.getEntries().stream()
                        .filter(entry -> DCAT.keyword.getURI().equals(entry.getIri()))
                        .findFirst();
        if (existingEntry.isPresent()) {
            existingEntry.get().setValue(newKeyword);
            return;
        }
        ontology.getEntries()
                .add(new OntologyEntry().setIri(DCAT.keyword.getURI()).setValue(newKeyword));
    }
}
