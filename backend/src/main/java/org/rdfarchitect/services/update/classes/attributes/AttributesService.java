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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AttributesService implements CreateAttributeUseCase, UpdateAttributesUseCase {

    private final DatabasePort databasePort;
    private final AttributeMapper attributeMapper;
    private final boolean newValuesAsBlankNode;

    public AttributesService(
            DatabasePort databasePort,
            AttributeMapper attributeMapper,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode) {
        this.databasePort = databasePort;
        this.attributeMapper = attributeMapper;
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
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            assertAttributeUriIsFree(graph, cimAttribute.getUri().toNode());
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
            var classLabel = findClassLabel(graph, attributeDTO.getDomain());
            ctx.commit(
                    "Created attribute \"%s.%s\" (%s)"
                            .formatted(
                                    classLabel, cimAttribute.getLabel(), cimAttribute.getUuid()));
        }
        return cimAttribute.getUuid();
    }

    @Override
    public UUID replaceAttribute(GraphIdentifier graphIdentifier, AttributeDTO attributeDTO) {
        var cimAttribute = attributeMapper.toCIMObject(attributeDTO);
        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var graphUri = graphIdentifier.graphUri();
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var update =
                    CIMUpdates.replaceAttribute(
                            graph, prefixMapping, graphUri, cimAttribute, newValuesAsBlankNode);
            UpdateExecutionFactory.create(
                            update, SessionDataStore.wrapGraphInDataset(graph, graphUri))
                    .execute();
            var classLabel = findClassLabel(graph, attributeDTO.getDomain());
            ctx.commit(
                    "Replaced attribute \"%s.%s\" (%s)"
                            .formatted(
                                    classLabel,
                                    cimAttribute.getLabel().getValue(),
                                    cimAttribute.getUuid()));
        }
        return cimAttribute.getUuid();
    }

    @Override
    public void replaceAllAttributes(
            GraphIdentifier graphIdentifier, UUID classUUID, List<AttributeDTO> attributeList) {
        var attributeCIMObjects = attributeMapper.toCIMObjectList(attributeList);
        var prefixMapping = databasePort.getPrefixMapping(graphIdentifier.datasetName());
        var graphUri = graphIdentifier.graphUri();
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
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
            var classResource = CIMResourceUtils.findResourceForUuid(graph, classUUID);
            var classLabel = CIMResourceUtils.findLabelForResource(classResource);
            ctx.commit(
                    "Replaced all attributes for class \"%s\" (%s)"
                            .formatted(classLabel, classUUID));
        }
    }

    private String findClassLabel(Graph graph, String domainUri) {
        var classResource = CIMResourceUtils.findResourceForUri(graph, domainUri);
        return CIMResourceUtils.findLabelForResource(classResource);
    }

    /**
     * Guards against creating an attribute whose IRI is already taken by another property. An
     * attribute's IRI is derived from its class and label ({@code <classIRI>.<label>}), so a second
     * attribute with the same label on the same class would reuse that IRI. Without this guard the
     * INSERT would stack a fresh {@code rdfa:uuid} (and any other diverging value) onto the
     * existing property node, turning it into an ambiguous resource that the class editor then
     * renders once per value combination.
     */
    private void assertAttributeUriIsFree(Graph graph, Node attributeUri) {
        if (graph.contains(attributeUri, RDF.type.asNode(), RDF.Property.asNode())) {
            throw new ResourceConflictException(
                    "Cannot create attribute "
                            + attributeUri.getURI()
                            + " because a property with the same IRI already exists.");
        }
    }
}
