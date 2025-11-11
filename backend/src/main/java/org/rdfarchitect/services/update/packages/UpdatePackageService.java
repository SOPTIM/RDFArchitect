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
import org.rdfarchitect.cim.changelog.ChangeLogEntry;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePackageService implements AddPackageUseCase, ReplacePackageUseCase, DeletePackageUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;
    private final ChangeLogUseCase changeLogUseCase;

    @Override
    public UUID addPackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        GraphRewindableWithUUIDs graph = null;
        UUID uuid = UUID.randomUUID();
        packageDTO.setUuid(uuid);
        try {
            graph = databasePort.getGraph(graphIdentifier);
            graph.begin(TxnType.WRITE);
            var newPackage = packageMapper.toCIMObject(packageDTO);
            CIMUpdates.insertPackage(graph, databasePort.getPrefixMapping(graphIdentifier.getDatasetName()), newPackage);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Added package " + packageDTO.getLabel(), graph.getLastDelta()));
        return uuid;
    }

    @Override
    public void replacePackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraph(graphIdentifier);
            graph.begin(TxnType.WRITE);
            var newPackage = packageMapper.toCIMObject(packageDTO);
            CIMUpdates.replacePackage(graph, databasePort.getPrefixMapping(graphIdentifier.getDatasetName()), newPackage);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
        changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Replaced package " + packageDTO.getUuid(), graph.getLastDelta()));
    }

    @Override
    public void deletePackage(GraphIdentifier graphIdentifier, UUID packageUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraph(graphIdentifier);
            graph.begin(TxnType.WRITE);
            CIMUpdates.deletePackage(graph, databasePort.getPrefixMapping(graphIdentifier.getDatasetName()), packageUUID.toString());
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
        changeLogUseCase.recordChange(graphIdentifier, new ChangeLogEntry("Deleted package " + packageUUID, graph.getLastDelta()));
    }
}
