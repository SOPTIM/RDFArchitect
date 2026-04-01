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

package org.rdfarchitect.services.delete;

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.delete.DeleteRelations;
import org.rdfarchitect.cim.relations.model.CIMResourceTypeIdentifyingUtils;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.springframework.stereotype.Service;
import org.rdfarchitect.cim.relations.model.CIMResourceTypeIdentifyingUtils.CimResourceType;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindOnDeleteRelationsService implements FindOnDeleteRelationsUseCase {

    private final DatabasePort databasePort;

    @Override
    public DeleteRelations getDeleteRelations(GraphIdentifier graphIdentifier, UUID uuid) {
        var model = ModelFactory.createModelForGraph(getCopyOfDatabaseGraph(graphIdentifier));
        var resourceType = CIMResourceTypeIdentifyingUtils.getType(model, uuid);
        return switch (resourceType) {
            case PACKAGE -> findAffectedRelationsForPackage(model, uuid);
            case CLASS -> findAffectedRelationsForClass(model, uuid);
            case ATTRIBUTE -> findAffectedRelationsForAttribute(model, uuid);
            case ASSOCIATION -> findAffectedRelationsForAssociation(model, uuid);
            case ENUM_ENTRY -> findAffectedRelationsForEnumEntry(model, uuid);
            case ONTOLOGY -> findAffectedRelationsForOntology(model, uuid);
            case UNKNOWN -> findAffectedRelationsForUnknown(model, uuid);
        };
    }

    private DeleteRelations findAffectedRelationsForPackage(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForClass(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForAttribute(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForAssociation(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForEnumEntry(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForOntology(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private DeleteRelations findAffectedRelationsForUnknown(Model model, UUID uuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private Graph getCopyOfDatabaseGraph(GraphIdentifier graphIdentifier){
        GraphRewindable graph = null;
        try{
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            return GraphUtils.deepCopy(graph);
        } finally {
            if(graph != null) {
                graph.end();
            }
        }
    }

}
