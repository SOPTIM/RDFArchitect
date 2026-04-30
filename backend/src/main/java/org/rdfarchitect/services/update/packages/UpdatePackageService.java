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

package org.rdfarchitect.services.update.packages;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.TxnType;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.ReplaceDiagramUseCase;
import org.rdfarchitect.services.dl.update.packagelayout.CreatePackageLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.packagelayout.DeletePackageLayoutDataUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePackageService
        implements AddPackageUseCase, ReplacePackageUseCase, DeletePackageUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;
    private final ChangeLogUseCase changeLogUseCase;

    private final CreatePackageLayoutDataUseCase createPackageLayoutData;
    private final ReplaceDiagramUseCase replaceDiagramUseCase;
    private final DeletePackageLayoutDataUseCase deletePackageLayoutDataUseCase;

    @Override
    public UUID addPackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        GraphRewindableWithUUIDs graph = null;
        UUID newPackageUUID = UUID.randomUUID();
        packageDTO.setUuid(newPackageUUID);
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var newPackage = packageMapper.toCIMObject(packageDTO);
            CIMUpdates.insertPackage(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    newPackage);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        createPackageLayoutData.createPackageLayoutData(
                graphIdentifier, packageDTO, newPackageUUID);

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Added package " + packageDTO.getLabel(), graph.getLastDelta()));
        return newPackageUUID;
    }

    @Override
    public void replacePackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var newPackage = packageMapper.toCIMObject(packageDTO);
            CIMUpdates.replacePackage(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    newPackage);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        replaceDiagramUseCase.replaceDiagram(
                graphIdentifier, packageDTO.getUuid(), packageDTO.getLabel());

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry(
                        "Replaced package " + packageDTO.getUuid(), graph.getLastDelta()));
    }

    @Override
    public void deletePackage(GraphIdentifier graphIdentifier, UUID packageUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            CIMUpdates.deletePackage(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    packageUUID.toString());
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        deletePackageLayoutDataUseCase.deletePackageLayoutData(graphIdentifier, packageUUID);

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Deleted package " + packageUUID, graph.getLastDelta()));
    }
}
