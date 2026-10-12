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
import org.rdfarchitect.services.ChangeDescriptions;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.diagrams.RemoveFromCustomDiagramUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CrossProfileDiagramLayoutUseCase;
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
    private final CrossProfileDiagramLayoutUseCase crossProfileDiagramLayoutUseCase;

    public UpdateClassService(
            DatabasePort databasePort,
            ClassUMLAdaptedMapper classMapper,
            PackageMapper packageMapper,
            CreateClassLayoutDataUseCase createClassLayoutDataUseCase,
            UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase,
            DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode,
            CrossProfileDiagramLayoutUseCase crossProfileDiagramLayoutUseCase,
            RemoveFromCustomDiagramUseCase removeFromCustomDiagramUseCase) {
        this.databasePort = databasePort;
        this.classMapper = classMapper;
        this.packageMapper = packageMapper;
        this.createClassLayoutDataUseCase = createClassLayoutDataUseCase;
        this.updateDiagramObjectNameUseCase = updateDiagramObjectNameUseCase;
        this.deleteClassLayoutDataUseCase = deleteClassLayoutDataUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
        this.crossProfileDiagramLayoutUseCase = crossProfileDiagramLayoutUseCase;
        this.removeFromCustomDiagramUseCase = removeFromCustomDiagramUseCase;
    }

    /**
     * Renaming a class also renames it in the layout, in the diagrams it appears in and in the
     * cross-profile view. One enclosing transaction keeps that one change in the history and one
     * step to undo, rather than four the user never asked for separately.
     */
    @Override
    public void replaceClass(GraphIdentifier graphIdentifier, ClassUMLAdaptedDTO newClass) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var graph = ctx.getRdfGraph();
            var oldClassUri =
                    CIMResourceUtils.findResourceForUuid(graph, newClass.getUuid()).getURI();

            var cimClass = classMapper.toCIMObject(newClass);
            assertNoPackageWithSameIri(graph, cimClass);
            var releasedUuid =
                    CIMUpdates.replaceClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            cimClass,
                            newValuesAsBlankNode);

            if (releasedUuid != null) {
                deleteClassLayoutDataUseCase.deleteClassLayoutData(graphIdentifier, releasedUuid);
                removeFromCustomDiagramUseCase.removeFromAllDiagrams(graphIdentifier, releasedUuid);
            }

            updateDiagramObjectNameUseCase.updateDiagramObjectName(
                    graphIdentifier, newClass.getUuid(), newClass.getLabel());

            String newClassUri = newClass.getPrefix() + newClass.getLabel();
            if (!oldClassUri.equals(newClassUri)) {
                crossProfileDiagramLayoutUseCase.migrateLayoutToNewClassUri(
                        graphIdentifier.datasetName(),
                        CrossProfileUtils.mergedUuid(oldClassUri),
                        CrossProfileUtils.mergedUuid(newClassUri),
                        newClassUri);
            }

            transaction.commit(describeClassUpdate(oldClassUri, newClass.getLabel()));
        }
    }

    /** Names a rename by both names, since that is the change the reader will be looking for. */
    private static String describeClassUpdate(String oldClassUri, String newLabel) {
        var oldLabel = ChangeDescriptions.localName(oldClassUri);
        return oldLabel.equals(newLabel)
                ? "Updated class \"%s\"".formatted(newLabel)
                : "Renamed class \"%s\" to \"%s\"".formatted(oldLabel, newLabel);
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var graph = ctx.getRdfGraph();
            var newClass = constructClass(cimPackage, classURIPrefix, className);
            assertNoPackageWithSameIri(graph, newClass);
            newClassUUID =
                    CIMUpdates.insertClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            newClass);
            createClassLayoutDataUseCase.createClassLayoutData(
                    graphIdentifier, packageDTO, className, newClassUUID, classLayoutPosition);
            transaction.commit("Added class \"%s\"".formatted(newClass.getLabel()));
        }

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

    /**
     * Deleting a class also takes its layout and its appearances in diagrams with it. As with a
     * rename, one enclosing transaction keeps that one change in the history and one step to undo.
     */
    @Override
    public void deleteClass(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var classResource = CIMResourceUtils.findResourceForUuid(ctx.getRdfGraph(), classUUID);
            var classLabel = CIMResourceUtils.findLabelForResource(classResource);
            CIMUpdates.deleteClass(
                    ctx.getRdfGraph(),
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    classUUID);

            deleteClassLayoutDataUseCase.deleteClassLayoutData(graphIdentifier, classUUID);
            removeFromCustomDiagramUseCase.removeFromAllDiagrams(graphIdentifier, classUUID);

            transaction.commit("Deleted class \"%s\"".formatted(classLabel));
        }
    }
}
