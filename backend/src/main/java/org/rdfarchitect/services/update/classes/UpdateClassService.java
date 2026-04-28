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

package org.rdfarchitect.services.update.classes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutor;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.DeleteClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateDiagramObjectNameUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateClassService
        implements AddClassUseCase, ReplaceClassUseCase, DeleteClassUseCase, CopyClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;
    private final ChangeLogUseCase changeLogUseCase;
    private final AttributeMapper attributeMapper;
    private final EnumEntryMapper enumEntryMapper;

    private final CreateClassLayoutDataUseCase createClassLayoutDataUseCase;
    private final UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase;
    private final DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase;

    @Override
    public void replaceClass(GraphIdentifier graphIdentifier, ClassUMLAdaptedDTO newClass) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var cimClass = classMapper.toCIMObject(newClass);
            CIMUpdates.replaceClass(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    cimClass);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        updateDiagramObjectNameUseCase.updateDiagramObjectName(
                graphIdentifier, newClass.getUuid(), newClass.getLabel());

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Updated class " + newClass.getUuid(), graph.getLastDelta()));
    }

    @Override
    public void addClass(
            GraphIdentifier graphIdentifier,
            PackageDTO packageDTO,
            String classURIPrefix,
            String className) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        GraphRewindableWithUUIDs graph = null;
        UUID newClassUUID;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);

            var newClass = constructClass(cimPackage, classURIPrefix, className);
            newClassUUID =
                    CIMUpdates.insertClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            newClass);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        createClassLayoutDataUseCase.createClassLayoutData(
                graphIdentifier, packageDTO, className, newClassUUID);

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Added class " + className, graph.getLastDelta()));
    }

    private CIMClass constructClass(
            CIMPackage cimPackage, String classURIPrefix, String classLabel) {
        var cimClass =
                CIMClass.builder()
                        .uri(new URI(classURIPrefix + classLabel))
                        .label(new RDFSLabel(classLabel, "en"))
                        .superClass(null)
                        .comment(null);
        if (cimPackage != null) {
            cimClass.belongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }
        return cimClass.build();
    }

    @Override
    public void deleteClass(GraphIdentifier graphIdentifier, String classUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            CIMUpdates.deleteClass(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    classUUID);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        deleteClassLayoutDataUseCase.deleteClassLayoutData(
                graphIdentifier, UUID.fromString(classUUID));

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Deleted class: " + classUUID, graph.getLastDelta()));
    }

    @Override
    public void copyClass(
            GraphIdentifier graphIdentifier,
            String classUUID,
            GraphIdentifier targetGraphIdentifier,
            PackageDTO targetPackageDTO,
            boolean copyAbstract) {
        GraphRewindableWithUUIDs graph = null, targetGraph = null;
        CIMClassUMLAdapted cimClass;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            cimClass =
                    CIMUMLObjectFactory.createCIMClassUMLAdapted(
                            graph,
                            graphIdentifier.graphUri(),
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            classUUID);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        RDFSLabel label =
                constructCopyLabel(graph, graphIdentifier.graphUri(), cimClass.getLabel());
        var cimPackage = packageMapper.toCIMObject(targetPackageDTO);
        UUID newClassUUID;
        var newCimClass = copyCimClass(cimClass, cimPackage, label, copyAbstract);
        String className = label.getValue();

        try {
            targetGraph = databasePort.getGraphWithContext(targetGraphIdentifier).getRdfGraph();
            targetGraph.begin(TxnType.WRITE);
            newClassUUID =
                    CIMUpdates.insertClass(
                            targetGraph,
                            databasePort.getPrefixMapping(targetGraphIdentifier.datasetName()),
                            newCimClass);
            targetGraph.commit();
        } finally {
            if (targetGraph != null) {
                targetGraph.end();
            }
        }

        try {
            if (!copyAbstract) {
                GraphRewindableWithUUIDs finalTargetGraph = targetGraph;
                copyAttributes(cimClass.getAttributes(), newCimClass)
                        .forEach(
                                cimAttribute -> {
                                    var update =
                                            CIMUpdates.insertAttribute(
                                                    databasePort.getPrefixMapping(
                                                            targetGraphIdentifier.datasetName()),
                                                    targetGraphIdentifier.graphUri(),
                                                    cimAttribute);
                                    InMemorySparqlExecutor.executeSingleUpdate(
                                            finalTargetGraph,
                                            update.build(),
                                            targetGraphIdentifier.graphUri());
                                });
                targetGraph.begin(TxnType.WRITE);
                copyEnumEntries(cimClass.getEnumEntries(), newCimClass)
                        .forEach(
                                cimEnumEntry -> CIMUpdates.insertEnumEntry(
                                        finalTargetGraph,
                                        databasePort.getPrefixMapping(
                                                targetGraphIdentifier.datasetName()),
                                        cimEnumEntry));
                targetGraph.commit();
            }
        } finally {
            if (!copyAbstract) {
                targetGraph.end();
            }
        }

        createClassLayoutDataUseCase.createClassLayoutData(
                targetGraphIdentifier, targetPackageDTO, className, newClassUUID);

        changeLogUseCase.recordChange(
                targetGraphIdentifier,
                new ChangeLogEntry("Added class " + className, targetGraph.getLastDelta()));
    }

    private CIMClass copyCimClass(
            CIMClassUMLAdapted cimClass,
            CIMPackage cimPackage,
            RDFSLabel label,
            boolean copyAbstract) {
        var newCimClass =
                CIMClassUMLAdapted.builder()
                        .uri(new URI(cimClass.getUri().getPrefix() + label.getValue()))
                        .label(label)
                        .superClass(cimClass.getSuperClass())
                        .stereotypes(
                                copyAbstract
                                        ? cimClass.getStereotypes().stream()
                                          .filter(
                                                  s ->
                                                  !s.getStereotype()
                                                   .equals(
                                                           CIMStereotypes
                                                           .concreteString))
                                          .toList()
                                        : cimClass.getStereotypes());
        if (cimClass.getComment() != null) {
            newCimClass.comment(
                    new RDFSComment(
                            cimClass.getComment().getValue(), cimClass.getComment().getFormat()));
        }
        if (cimPackage != null) {
            newCimClass.belongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }

        return newCimClass.build();
    }

    private List<CIMAttribute> copyAttributes(List<CIMAttribute> attributes, CIMClass cimClass) {
        return attributeMapper.toDTOList(attributes).stream()
                .map(
                        dto -> {
                            dto.setUuid(UUID.randomUUID());
                            dto.setDomain(cimClass.getUri().toString());
                            return attributeMapper.toCIMObject(dto);
                        })
                .toList();
    }

    private List<CIMEnumEntry> copyEnumEntries(List<CIMEnumEntry> enumEntries, CIMClass cimClass) {
        return enumEntryMapper.toDTOList(enumEntries).stream()
                .map(
                        dto -> {
                            dto.setUuid(UUID.randomUUID());
                            dto.setType(cimClass.getUri().toString());
                            return enumEntryMapper.toCIMObject(dto);
                        })
                .toList();
    }

    private RDFSLabel constructCopyLabel(
            GraphRewindableWithUUIDs graph, String graphUri, RDFSLabel label) {
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addGraph(
                                NodeFactory.createURI(graphUri),
                                new SelectBuilder().addWhere("?s", RDFS.label, "?label"))
                        .build();
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graph, query, graphUri);

        Set<String> existingLabels = new HashSet<>();
        while (resultSet.hasNext()) {
            var solution = resultSet.nextSolution();
            existingLabels.add(solution.getLiteral("label").getString());
        }

        String baseValue = label.getValue() + " - Copy";
        if (!existingLabels.contains(baseValue)) {
            return new RDFSLabel(baseValue, label.getLang());
        }

        int counter = 1;
        while (existingLabels.contains(baseValue + "(" + counter + ")")) {
            counter++;
        }

        return new RDFSLabel(baseValue + "(" + counter + ")", label.getLang());
    }
}
