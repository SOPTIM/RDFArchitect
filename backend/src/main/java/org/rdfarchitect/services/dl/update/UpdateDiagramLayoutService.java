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

package org.rdfarchitect.services.dl.update;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterUseCase;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateDiagramLayoutService implements CreateDiagramLayoutUseCase {

    private static final String DEFAULT_PACKAGE_NAME = "default";

    private final DatabasePort databasePort;
    private final GraphToCIMCollectionConverterUseCase converter;

    @Override
    public void createDiagramLayout(GraphIdentifier graphIdentifier) {
        var allPackagesGraphFilter = new GraphFilter(false);
        var allPackagesCIMCollection = converter.convert(graphIdentifier, allPackagesGraphFilter);

        var packageGraphFilter = new GraphFilter(false);
        packageGraphFilter.setIncludeInheritance(true);
        packageGraphFilter.setIncludeAssociations(true);
        packageGraphFilter.setIncludeRelationsToExternalPackages(true);
        packageGraphFilter.setPackageUUID(null);
        var defaultPackageClassesCIMCollection =
                converter.convert(graphIdentifier, packageGraphFilter);

        Map<CIMPackage, CIMCollection> classesByPackage = new LinkedHashMap<>();
        for (var cimPackage : allPackagesCIMCollection.getPackages()) {
            packageGraphFilter.setPackageUUID(cimPackage.getUuid().toString());
            classesByPackage.put(
                    cimPackage, converter.convert(graphIdentifier, packageGraphFilter));
        }

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();

            DLUpdates.insertDiagram(
                    diagramLayoutModel,
                    Diagram.builder()
                            .mRID(diagramLayout.getDefaultPackageMRID())
                            .name(graphIdentifier.graphUri() + "/" + DEFAULT_PACKAGE_NAME)
                            .orientation(OrientationKind.NEGATIVE)
                            .build());

            for (var cimClassOrEnum : defaultPackageClassesCIMCollection.getClassesAndEnums()) {
                var doMRID =
                        DiagramLayoutServiceUtils.insertDiagramObject(
                                diagramLayoutModel,
                                diagramLayout.getDefaultPackageMRID().getUuid(),
                                cimClassOrEnum.getLabel().getValue(),
                                cimClassOrEnum.getUuid());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, doMRID);
            }

            for (var entry : classesByPackage.entrySet()) {
                var cimPackage = entry.getKey();
                DiagramLayoutServiceUtils.insertDiagram(
                        diagramLayoutModel, cimPackage.getUuid(), cimPackage.getLabel().getValue());
                for (var cimClassOrEnum : entry.getValue().getClassesAndEnums()) {
                    var doMRID =
                            DiagramLayoutServiceUtils.insertDiagramObject(
                                    diagramLayoutModel,
                                    cimPackage.getUuid(),
                                    cimClassOrEnum.getLabel().getValue(),
                                    cimClassOrEnum.getUuid());
                    DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, doMRID);
                }
            }
            ctx.commit();
        }
    }
}
