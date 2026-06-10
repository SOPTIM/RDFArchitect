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

package org.rdfarchitect.services.rendering;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.api.dto.crossProfileDiagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.crossProfileDiagram.MergedClassDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.CIMMergedClass;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiagramToCIMCollectionConverterService
        implements DiagramToCIMCollectionConverterUseCase {

    private final DatabasePort databasePort;

    private final GraphToCIMCollectionConverterService converter;

    private final AttributeMapper attributeMapper;
    private final EnumEntryMapper enumEntryMapper;
    private final AssociationPairMapper associationPairMapper;

    @Override
    public CIMCollection convert(GraphIdentifier graphIdentifier, String diagramId) {
        var diagrams = databasePort.getGraphWithContext(graphIdentifier).getCustomDiagrams();
        var diagramUUID = UUID.fromString(diagramId);
        if (!diagrams.containsKey(diagramUUID)) {
            throw new IllegalArgumentException(
                    "Diagram with ID " + diagramId + " not found in graph " + graphIdentifier);
        }

        var diagram = diagrams.get(diagramUUID);
        var classUUIDs =
                diagram.getClasses().stream().map(cls -> cls.getUuid().toString()).toList();
        var filter = new GraphFilter(true);
        filter.setIncludeRelationsToExternalPackages(false);
        filter.setAllowedUUIDs(classUUIDs);
        return converter.convert(graphIdentifier, filter);
    }

    @Override
    public CIMCollection convert(String datasetName, String diagramId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        var diagramUUID = UUID.fromString(diagramId);
        if (!diagrams.containsKey(diagramUUID)) {
            throw new IllegalArgumentException(
                    "Diagram with ID " + diagramId + " not found in dataset " + datasetName);
        }

        var diagram = diagrams.get(diagramUUID);
        // Group ClassInDiagram entries by graphUri
        var classesByGraph =
                diagram.getClasses().stream()
                        .collect(Collectors.groupingBy(ClassInDiagram::getGraphUri));

        var mergedCollection = new CIMCollection();

        for (var entry : classesByGraph.entrySet()) {
            var graphIdentifier = new GraphIdentifier(datasetName, entry.getKey().toString());
            var classUUIDs = entry.getValue().stream().map(c -> c.getUuid().toString()).toList();

            var filter = new GraphFilter(true);
            filter.setIncludeRelationsToExternalPackages(false);
            filter.setAllowedUUIDs(classUUIDs);

            var partial = converter.convert(graphIdentifier, filter);
            mergeInto(mergedCollection, partial);
        }

        return mergedCollection;
    }

    private void mergeInto(CIMCollection target, CIMCollection source) {
        target.getPackages().addAll(source.getPackages());
        target.getClasses().addAll(source.getClasses());
        target.getEnums().addAll(source.getEnums());
        target.getAttributes().addAll(source.getAttributes());
        target.getEnumEntries().addAll(source.getEnumEntries());
        target.getAssociations().addAll(source.getAssociations());
    }

    @Override
    public CIMCollection convert(CrossProfileDiagramDTO diagram) {
        var collection = new CIMCollection();

        for (var mergedClass : diagram.getClasses()) {
            var uri = new URI(mergedClass.getClassUri());
            var superClasses = new ArrayList<RDFSSubClassOf>();
            for (var superClassDTO : mergedClass.getSuperClasses()) {
                var superClass = superClassDTO.getValue();
                superClasses.add(
                        new RDFSSubClassOf(
                                new URI(superClass.getPrefix() + superClass.getLabel()),
                                new RDFSLabel(superClass.getLabel())));
            }
            var label = new RDFSLabel(uri.getSuffix());
            var cimClass =
                    CIMMergedClass.builder()
                            .uuid(mergedClass.getUuid())
                            .uri(uri)
                            .label(label)
                            .superClasses(superClasses)
                            .stereotypes(mergedClass.getStereotypes())
                            .build();
            collection.getClasses().add(cimClass);

            convertAttributes(mergedClass, collection);
            convertEnumEntries(mergedClass, collection);
            convertAssociationPairs(mergedClass, collection);
        }

        return collection;
    }

    private void convertAssociationPairs(MergedClassDTO mergedClass, CIMCollection collection) {
        for (var graphSourcedPair : mergedClass.getAssociationPairs()) {
            var pair = associationPairMapper.toCIMObject(graphSourcedPair.getValue());
            var graphUri = graphSourcedPair.getGraphUri();
            if (pair.getFrom() != null) {
                collection
                        .getAssociations()
                        .add(pair.getFrom().toBuilder().graphUri(graphUri).build());
            }
            if (pair.getTo() != null) {
                collection
                        .getAssociations()
                        .add(pair.getTo().toBuilder().graphUri(graphUri).build());
            }
        }
    }

    private void convertEnumEntries(MergedClassDTO mergedClass, CIMCollection collection) {
        for (var graphSourcedEntry : mergedClass.getEnumEntries()) {
            var cimEnumEntry = enumEntryMapper.toCIMObject(graphSourcedEntry.getValue());
            cimEnumEntry =
                    cimEnumEntry.toBuilder()
                            .type(
                                    new RDFType(
                                            new URI(mergedClass.getClassUri()),
                                            new RDFSLabel(graphSourcedEntry.getValue().getLabel())))
                            .graphUri(graphSourcedEntry.getGraphUri())
                            .color(graphSourcedEntry.getGraphColor())
                            .build();
            collection.getEnumEntries().add(cimEnumEntry);
        }
    }

    private void convertAttributes(MergedClassDTO mergedClass, CIMCollection collection) {
        for (var graphSourcedAttr : mergedClass.getAttributes()) {
            var cimAttribute = attributeMapper.toCIMObject(graphSourcedAttr.getValue());
            cimAttribute =
                    cimAttribute.toBuilder()
                            .graphUri(graphSourcedAttr.getGraphUri())
                            .color(graphSourcedAttr.getGraphColor())
                            .build();
            collection.getAttributes().add(cimAttribute);
        }
    }
}
