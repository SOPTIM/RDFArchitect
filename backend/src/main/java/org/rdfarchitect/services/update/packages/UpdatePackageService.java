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

import static org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder.Mode.REQUIRED;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutor;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.data.CIMObjectFactory;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.queries.select.CIMBaseQueryBuilder;
import org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
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
        implements AddPackageUseCase,
                ReplacePackageUseCase,
                DeletePackageUseCase,
                GetPackageUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;

    private final CreatePackageLayoutDataUseCase createPackageLayoutData;
    private final ReplaceDiagramUseCase replaceDiagramUseCase;
    private final DeletePackageLayoutDataUseCase deletePackageLayoutDataUseCase;

    @Override
    public UUID addPackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        UUID newPackageUUID = UUID.randomUUID();
        packageDTO.setUuid(newPackageUUID);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var newPackage = packageMapper.toCIMObject(packageDTO);
            assertNoClassWithSameIri(graph, newPackage);
            CIMUpdates.insertPackage(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    newPackage);
            ctx.commit("Added package " + packageDTO.getLabel());
        }

        createPackageLayoutData.createPackageLayoutData(
                graphIdentifier, packageDTO, newPackageUUID);

        return newPackageUUID;
    }

    @Override
    public void replacePackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var newPackage = packageMapper.toCIMObject(packageDTO);
            assertNoClassWithSameIri(graph, newPackage);
            CIMUpdates.replacePackage(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    newPackage);
            ctx.commit("Replaced package " + packageDTO.getUuid());
        }

        replaceDiagramUseCase.replaceDiagram(
                graphIdentifier, packageDTO.getUuid(), packageDTO.getLabel());
    }

    @Override
    public void deletePackage(GraphIdentifier graphIdentifier, UUID packageUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            CIMUpdates.deletePackage(
                    ctx.getRdfGraph(),
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    packageUUID);
            ctx.commit("Deleted package " + packageUUID);
        }

        deletePackageLayoutDataUseCase.deletePackageLayoutData(graphIdentifier, packageUUID);
    }

    private void assertNoClassWithSameIri(Graph graph, CIMPackage newPackage) {
        var packageUri = newPackage.getUri().toNode();
        if (graph.contains(packageUri, RDF.type.asNode(), RDFS.Class.asNode())) {
            throw new ResourceConflictException(
                    "Cannot save package "
                            + newPackage.getUri()
                            + " because a class with the same IRI already exists.");
        }
    }

    @Override
    public PackageDTO getPackage(GraphIdentifier graphIdentifier, UUID packageUUID) {
        var baseQuery =
                new CIMBaseQueryBuilder()
                        .setDistinct()
                        .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()))
                        .setGraph(graphIdentifier.graphUri())
                        .setType(CIMS.classCategory)
                        .addWhereThis(RDFA.uuid, packageUUID.toString())
                        .build();

        var query =
                new CIMQueryBuilder(baseQuery)
                        .appendUUIDQuery(REQUIRED)
                        .appendLabelQuery(REQUIRED)
                        .build();
        try(var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)){
            var resultSet =
                    InMemorySparqlExecutor.executeSingleQuery(
                            ctx.getRdfGraph(),
                            query,
                            graphIdentifier.graphUri());

            if (!resultSet.hasNext()) {
                throw new DataAccessException("Package not found: " + packageUUID);
            }

            var cimPackage = CIMObjectFactory.createCIMPackage(resultSet.nextSolution());
            return packageMapper.toDTO(cimPackage);
        }
    }
}
