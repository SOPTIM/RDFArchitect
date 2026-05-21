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

package org.rdfarchitect.services.dl.update.packagelayout;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.dl.update.ReplaceDiagramUseCase;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePackageLayoutService
        implements CreatePackageLayoutDataUseCase,
                DeletePackageLayoutDataUseCase,
                ReplaceDiagramUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;
    private final GraphToCIMCollectionConverterUseCase converter;

    @Override
    public void createPackageLayoutData(
            GraphIdentifier graphIdentifier, PackageDTO packageDTO, UUID newPackageUUID) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        cimPackage.setUuid(newPackageUUID);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            DiagramLayoutServiceUtils.insertDiagram(
                    diagramLayoutModel, cimPackage.getUuid(), cimPackage.getLabel().getValue());
            ctx.commit();
        }
    }

    @Override
    public void deletePackageLayoutData(GraphIdentifier graphIdentifier, UUID packageUUID) {
        // converter.convert() opens its own READ transaction — collect data before opening WRITE
        var packageGraphFilter = new GraphFilter(false);
        packageGraphFilter.setIncludeInheritance(true);
        packageGraphFilter.setIncludeAssociations(true);
        packageGraphFilter.setIncludeRelationsToExternalPackages(true);
        packageGraphFilter.setPackageUUID(packageUUID.toString());
        var classesCIMCollection = converter.convert(graphIdentifier, packageGraphFilter);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();

            for (var cimClassOrEnum : classesCIMCollection.getClassesAndEnums()) {
                if (cimClassOrEnum.getBelongsToCategory().getUuid() == packageUUID) {
                    for (var diagramObject :
                            DLObjectFetcher.fetchAllDOs(
                                    diagramLayoutModel, cimClassOrEnum.getUuid())) {
                        DLUpdates.deleteDiagramObjectCascade(
                                diagramLayoutModel, diagramObject.getMRID());
                    }
                } else {
                    var diagramObject =
                            DLObjectFetcher.fetchDiagramDOForClass(
                                    diagramLayoutModel, packageUUID, cimClassOrEnum.getUuid());
                    DLUpdates.deleteDiagramObjectCascade(
                            diagramLayoutModel, diagramObject.getMRID());
                }
            }

            DLUpdates.deleteDiagram(diagramLayoutModel, new MRID(packageUUID));
            ctx.commit();
        }
    }

    @Override
    public void replaceDiagram(
            GraphIdentifier graphIdentifier, UUID packageUUID, String packageName) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            var diagramMRID = new MRID(packageUUID);
            var newDiagram =
                    Diagram.builder()
                            .mRID(diagramMRID)
                            .name(packageName)
                            .orientation(OrientationKind.NEGATIVE)
                            .build();
            DLUpdates.replaceDiagram(diagramLayoutModel, diagramMRID, newDiagram);
            ctx.commit();
        }
    }
}
