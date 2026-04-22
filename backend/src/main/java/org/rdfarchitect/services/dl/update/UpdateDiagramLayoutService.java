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
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.OrientationKind;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateDiagramLayoutService implements CreateDiagramLayoutUseCase, EnsureDiagramLayoutForCIMCollectionUseCase {

    final String DEFAULT_PACKAGE_NAME = "default";

    private final DatabasePort databasePort;
    private final GraphToCIMCollectionConverterUseCase converter;

    @Override
    public void createDiagramLayout(GraphIdentifier graphIdentifier) {
        var diagramLayout = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();

        var allPackagesGraphFilter = new GraphFilter(false);
        CIMCollection allPackagesCIMCollection = converter.convert(graphIdentifier, allPackagesGraphFilter);

        //set filter to also include classes outside the package
        var packageGraphFilter = new GraphFilter(false);
        packageGraphFilter.setIncludeInheritance(true);
        packageGraphFilter.setIncludeAssociations(true);
        packageGraphFilter.setIncludeRelationsToExternalPackages(true);

        //create DL diagram for the default package
        DLUpdates.insertDiagram(diagramLayoutModel, Diagram.builder()
                                                           .mRID(diagramLayout.getDefaultPackageMRID())
                                                           .name(graphIdentifier.getGraphUri() + "/" + DEFAULT_PACKAGE_NAME)
                                                           .orientation(OrientationKind.NEGATIVE)
                                                           .build());
        //create DOs and DOPs for the default package
        packageGraphFilter.setPackageUUID(null);
        var defaultPackageClassesCIMCollection = converter.convert(graphIdentifier, packageGraphFilter);
        for (var cimClassOrEnum : defaultPackageClassesCIMCollection.getClassesAndEnums()) {
            var diagramObjectMRID = DiagramLayoutServiceUtils.insertDiagramObject(diagramLayoutModel,
                                                                                  diagramLayout.getDefaultPackageMRID().getUuid(),
                                                                                  cimClassOrEnum.getLabel().getValue(),
                                                                                  cimClassOrEnum.getUuid());
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectMRID);
        }

        //create DOs and DOPs for each frontend diagram
        for (var cimPackage : allPackagesCIMCollection.getPackages()) {
            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, cimPackage.getUuid(), cimPackage.getLabel().getValue());

            packageGraphFilter.setPackageUUID(cimPackage.getUuid().toString());
            var classesCIMCollection = converter.convert(graphIdentifier, packageGraphFilter);

            for (var cimClassOrEnum : classesCIMCollection.getClassesAndEnums()) {
                var diagramObjectMRID = DiagramLayoutServiceUtils.insertDiagramObject(diagramLayoutModel,
                                                                                      cimPackage.getUuid(),
                                                                                      cimClassOrEnum.getLabel().getValue(),
                                                                                      cimClassOrEnum.getUuid());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectMRID);
            }
        }
    }

    @Override
    public void ensureDiagramLayoutExists(GraphIdentifier graphIdentifier, UUID packageUUID, CIMCollection cimCollection) {
        var diagramLayout = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();

        var resolvedPackageUUID = packageUUID != null ?
                                  packageUUID :
                                  diagramLayout.getDefaultPackageMRID().getUuid();

        //ensure a DL diagram exists for the requested package
        var diagram = DLObjectFetcher.fetchDiagram(diagramLayoutModel, resolvedPackageUUID);
        if (diagram == null) {
            String packageName = null;

            //finds the package name for the DL diagram to be added
            if(resolvedPackageUUID == diagramLayout.getDefaultPackageMRID().getUuid()) {
                packageName = graphIdentifier.getGraphUri() + "/" + DEFAULT_PACKAGE_NAME;
            }
            else {
                for (var cimPackage : cimCollection.getPackages()) {
                    if (cimPackage.getUuid().equals(resolvedPackageUUID)) {
                        packageName = cimPackage.getLabel().getValue();
                        break;
                    }
                }

                if (packageName == null) {
                    throw new IllegalArgumentException("Package with UUID " + resolvedPackageUUID + " not found");
                }
            }

            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, resolvedPackageUUID, packageName);        }

        //ensure DOs and DOPs exist for all classes to be rendered
        var cimClasses = cimCollection.getClassesAndEnums();
        var diagramObjects = DLObjectFetcher.fetchDiagramDOs(diagramLayoutModel, new MRID(resolvedPackageUUID));
        var existingMRIDs = diagramObjects.stream()
                                          .map(DiagramObject::getBelongsToIdentifiedObject)
                                          .collect(Collectors.toSet());
        for (var cimClass : cimClasses) {
            if (!existingMRIDs.contains(new MRID(cimClass.getUuid()))) {
                var diagramObjectMRID = DiagramLayoutServiceUtils.insertDiagramObject(diagramLayoutModel,
                                                                                      resolvedPackageUUID,
                                                                                      cimClass.getLabel().getValue(),
                                                                                      cimClass.getUuid());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectMRID);
            }
        }
    }
}
