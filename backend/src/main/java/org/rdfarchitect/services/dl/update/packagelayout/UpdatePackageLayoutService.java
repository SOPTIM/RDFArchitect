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
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.dl.update.ReplaceDiagramUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePackageLayoutService implements CreatePackageLayoutDataUseCase, DeletePackageLayoutDataUseCase, ReplaceDiagramUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;
    private final GraphToCIMCollectionConverterUseCase converter;

    @Override
    public void createPackageLayoutData(GraphIdentifier graphIdentifier, PackageDTO packageDTO, UUID newPackageUUID) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        cimPackage.setUuid(newPackageUUID);

        var diagramLayoutModel = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, cimPackage.getUuid(), cimPackage.getLabel().getValue());
    }

    @Override
    public void deletePackageLayoutData(GraphIdentifier graphIdentifier, UUID packageUUID) {
        var diagramLayoutModel = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var packageGraphFilter = new GraphFilter(false);
        packageGraphFilter.setIncludeInheritance(true);
        packageGraphFilter.setIncludeAssociations(true);
        packageGraphFilter.setIncludeRelationsToExternalPackages(true);
        packageGraphFilter.setPackageUUID(packageUUID.toString());

        var classesCIMCollection = converter.convert(graphIdentifier, packageGraphFilter);

        for (var cimClassOrEnum : classesCIMCollection.getClassesAndEnums()) {
            //if the class belongs to the package, delete all DOs and DOPs globally
            if (cimClassOrEnum.getBelongsToCategory().getUuid() == packageUUID) {
                for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, cimClassOrEnum.getUuid())) {
                    DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
                }
            }
            //if the class belongs to a different package, only delete the DO/DOP in the diagram
            else {
                var diagramObject = DLObjectFetcher.fetchDiagramDOForClass(diagramLayoutModel, packageUUID, cimClassOrEnum.getUuid());
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
        }

        DLUpdates.deleteDiagram(diagramLayoutModel, new MRID(packageUUID));
    }

    @Override
    public void replaceDiagram(GraphIdentifier graphIdentifier, UUID packageUUID, String packageName) {
        var diagramLayoutModel = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();
        var diagramMRID = new MRID(packageUUID);

        var newDiagram = Diagram.builder()
                                .mRID(diagramMRID)
                                .name(packageName)
                                .orientation(OrientationKind.NEGATIVE)
                                .build();

        DLUpdates.replaceDiagram(diagramLayoutModel, diagramMRID, newDiagram);
    }
}
