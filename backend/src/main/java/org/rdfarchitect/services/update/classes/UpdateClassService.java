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

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.TxnType;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.DeleteClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateDiagramObjectNameUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateClassService
        implements AddClassUseCase, ReplaceClassUseCase, DeleteClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;
    private final ChangeLogUseCase changeLogUseCase;

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
                    graph, databasePort.getPrefixMapping(graphIdentifier.datasetName()), cimClass);
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
                    graph, databasePort.getPrefixMapping(graphIdentifier.datasetName()), classUUID);
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
}
