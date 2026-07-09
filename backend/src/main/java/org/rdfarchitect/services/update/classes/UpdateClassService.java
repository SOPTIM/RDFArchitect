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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.dl.ClassLayoutPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.services.diagrams.RemoveFromCustomDiagramUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.DeleteClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateDiagramObjectNameUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateClassService
        implements AddClassUseCase, ReplaceClassUseCase, DeleteClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;
    private final boolean newValuesAsBlankNode;

    private final CreateClassLayoutDataUseCase createClassLayoutDataUseCase;
    private final UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase;
    private final DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase;
    private final RemoveFromCustomDiagramUseCase removeFromCustomDiagramUseCase;

    public UpdateClassService(
            DatabasePort databasePort,
            ClassUMLAdaptedMapper classMapper,
            PackageMapper packageMapper,
            CreateClassLayoutDataUseCase createClassLayoutDataUseCase,
            UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase,
            DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode,
            RemoveFromCustomDiagramUseCase removeFromCustomDiagramUseCase) {
        this.databasePort = databasePort;
        this.classMapper = classMapper;
        this.packageMapper = packageMapper;
        this.createClassLayoutDataUseCase = createClassLayoutDataUseCase;
        this.updateDiagramObjectNameUseCase = updateDiagramObjectNameUseCase;
        this.deleteClassLayoutDataUseCase = deleteClassLayoutDataUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
        this.removeFromCustomDiagramUseCase = removeFromCustomDiagramUseCase;
    }

    @Override
    public void replaceClass(GraphIdentifier graphIdentifier, ClassUMLAdaptedDTO newClass) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var cimClass = classMapper.toCIMObject(newClass);
            assertNoPackageWithSameIri(graph, cimClass);
            CIMUpdates.replaceClass(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    cimClass,
                    newValuesAsBlankNode);
            ctx.commit(
                    "Updated class \"%s\" (%s)".formatted(newClass.getLabel(), newClass.getUuid()));
        }

        updateDiagramObjectNameUseCase.updateDiagramObjectName(
                graphIdentifier, newClass.getUuid(), newClass.getLabel());
    }

    @Override
    public UUID addClass(
            GraphIdentifier graphIdentifier,
            PackageDTO packageDTO,
            String classURIPrefix,
            String className,
            ClassLayoutPositionDTO classLayoutPosition) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        UUID newClassUUID;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var newClass = constructClass(cimPackage, classURIPrefix, className);
            assertNoPackageWithSameIri(graph, newClass);
            newClassUUID =
                    CIMUpdates.insertClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            newClass);
            ctx.commit("Added class \"%s\" (%s)".formatted(newClass.getLabel(), newClassUUID));
        }

        createClassLayoutDataUseCase.createClassLayoutData(
                graphIdentifier, packageDTO, className, newClassUUID, classLayoutPosition);

        return newClassUUID;
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

    private void assertNoPackageWithSameIri(Graph graph, CIMClass newClass) {
        var classUri = newClass.getUri().toNode();
        if (graph.contains(classUri, RDF.type.asNode(), CIMS.classCategory.asNode())) {
            throw new ResourceConflictException(
                    "Cannot save class "
                            + newClass.getUri()
                            + " because a package with the same IRI already exists.");
        }
    }

    @Override
    public void deleteClass(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var classResource = CIMResourceUtils.findResourceForUuid(ctx.getRdfGraph(), classUUID);
            var classLabel = CIMResourceUtils.findLabelForResource(classResource);
            CIMUpdates.deleteClass(
                    ctx.getRdfGraph(),
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    classUUID);
            ctx.commit("Deleted class \"%s\" (%s)".formatted(classLabel, classUUID));
        }

        deleteClassLayoutDataUseCase.deleteClassLayoutData(graphIdentifier, classUUID);
        removeFromCustomDiagramUseCase.removeFromAllDiagrams(graphIdentifier, classUUID);
    }
}
