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

package org.rdfarchitect.services.select.ontology;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyField;
import org.rdfarchitect.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.cim.ontology.OntologyFacade;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadOntologyService implements ReadOntologyUseCase, GetKnownOntologyFieldsUseCase {

    private final DatabasePort databasePort;

    // READ
    @Override
    public OntologyDTO getCurrentOntology(GraphIdentifier graphIdentifier) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraph(graphIdentifier);
            graph.begin(TxnType.READ);
            var model = ModelFactory.createModelForGraph(graph);
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));

            var ontology = new OntologyFacade(model);
            return ontology.getOntology();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    @Override
    public List<OntologyField> getKnownOntologyFields() {
        return KnownOntologyFields.getAllFields();
    }
}
