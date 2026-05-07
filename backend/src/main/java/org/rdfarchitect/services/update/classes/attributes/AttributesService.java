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

package org.rdfarchitect.services.update.classes.attributes;

import org.apache.jena.query.TxnType;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateRequest;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AttributesService implements CreateAttributeUseCase, UpdateAttributesUseCase {

    private final DatabasePort databasePort;
    private final AttributeMapper attributeMapper;
    private final ChangeLogUseCase changeLogUseCase;
    private final boolean newValuesAsBlankNode;

    public AttributesService(
            DatabasePort databasePort,
            AttributeMapper attributeMapper,
            ChangeLogUseCase changeLogUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode) {
        this.databasePort = databasePort;
        this.attributeMapper = attributeMapper;
        this.changeLogUseCase = changeLogUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
    }

    @Override
    public UUID createAttribute(GraphIdentifier graphIdentifier, AttributeDTO attributeDTO) {
        var cimAttribute = attributeMapper.toCIMObject(attributeDTO);
        if (cimAttribute.getUuid() == null) {
            cimAttribute.setUuid(UUID.randomUUID());
        }
        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var graphUri = graphIdentifier.graphUri();
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        try {
            graph.begin(TxnType.WRITE);
            var update =
                    new UpdateRequest()
                            .add(
                                    CIMUpdates.insertAttribute(
                                                    graph,
                                                    prefixMapping,
                                                    graphUri,
                                                    cimAttribute,
                                                    newValuesAsBlankNode)
                                            .build());
            UpdateExecutionFactory.create(
                            update, SessionDataStore.wrapGraphInDataset(graph, graphUri))
                    .execute();
            graph.commit();
        } finally {
            graph.end();
        }
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Created attribute " + cimAttribute.getUuid(), graph.getLastDelta()));
        return cimAttribute.getUuid();
    }

    @Override
    public UUID replaceAttribute(GraphIdentifier graphIdentifier, AttributeDTO attributeDTO) {
        var cimAttribute = attributeMapper.toCIMObject(attributeDTO);
        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var graphUri = graphIdentifier.graphUri();
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        try {
            graph.begin(TxnType.WRITE);
            var update =
                    CIMUpdates.replaceAttribute(
                            graph, prefixMapping, graphUri, cimAttribute, newValuesAsBlankNode);
            UpdateExecutionFactory.create(
                            update, SessionDataStore.wrapGraphInDataset(graph, graphUri))
                    .execute();
            graph.commit();
        } finally {
            graph.end();
        }
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Replaced attribute " + cimAttribute.getUuid(), graph.getLastDelta()));
        return cimAttribute.getUuid();
    }

    @Override
    public void replaceAllAttributes(
            GraphIdentifier graphIdentifier, String classUUID, List<AttributeDTO> attributeList) {
        var attributeCIMObjects = attributeMapper.toCIMObjectList(attributeList);
        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var graphUri = graphIdentifier.graphUri();
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        try {
            graph.begin(TxnType.WRITE);
            var update =
                    CIMUpdates.replaceAttributes(
                            graph,
                            prefixMapping,
                            graphUri,
                            classUUID,
                            attributeCIMObjects,
                            newValuesAsBlankNode);
            UpdateExecutionFactory.create(
                            update, SessionDataStore.wrapGraphInDataset(graph, graphUri))
                    .execute();
            graph.commit();
        } finally {
            graph.end();
        }
        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "All attributes for class " + classUUID + " replaced",
                        graph.getLastDelta()));
    }
}
