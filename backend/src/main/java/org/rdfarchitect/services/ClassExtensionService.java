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

package org.rdfarchitect.services;

import lombok.AllArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.ClassExtensionResultDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.services.select.LocateClassUseCase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ClassExtensionService implements ClassExtensionUseCase {

    private DatabasePort databasePort;
    private LocateClassUseCase locateClassUseCase;

    @Override
    public List<ClassExtensionResultDTO> extendClasses(
            String datasetName,
            List<String> classUUIDs,
            GraphIdentifier newGraphIdentifier,
            boolean withInheritance) {
        var stubsBySourceUUID = new LinkedHashMap<String, CIMClass>();
        var superClassStubs = new ArrayList<CIMClass>();

        for (var classUUID : classUUIDs) {
            var located = locateClassUseCase.locate(datasetName, classUUID);
            var sourceGraph = new GraphIdentifier(datasetName, located.graphUri());
            try (var ctx = databasePort.getGraphWithContext(sourceGraph).begin(ReadWrite.READ)) {
                stubsBySourceUUID.put(
                        classUUID,
                        fetchStubbedClassCopy(sourceGraph, located.classUUID().toString()));
                if (withInheritance) {
                    superClassStubs.addAll(
                            fetchStubbedSuperClasses(ctx.getRdfGraph(), located.classUUID()));
                }
            }
        }

        var stubs = new ArrayList<>(superClassStubs);
        stubs.addAll(stubsBySourceUUID.values());
        var insertedUris = insertStubs(newGraphIdentifier, stubs);
        var identifiers = readTargetIdentifiers(newGraphIdentifier, stubsBySourceUUID.values());

        var results = new ArrayList<ClassExtensionResultDTO>();
        for (var entry : stubsBySourceUUID.entrySet()) {
            var uri = entry.getValue().getUri().toString();
            var targetIdentifiers = identifiers.get(uri);
            results.add(
                    new ClassExtensionResultDTO(
                            UUID.fromString(entry.getKey()),
                            targetIdentifiers.classUUID(),
                            targetIdentifiers.packageUUID(),
                            insertedUris.contains(uri)));
        }
        return results;
    }

    private CIMClass fetchStubbedClassCopy(GraphIdentifier graphIdentifier, String classUUID) {
        var cimObjectFetcher =
                new CIMObjectFetcher(
                        databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(),
                        graphIdentifier.graphUri(),
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()));

        var classCopy = cimObjectFetcher.fetchCIMClass(classUUID);
        if (classCopy == null) {
            throw new IllegalArgumentException(
                    "Class with UUID "
                            + classUUID
                            + " not found in graph "
                            + graphIdentifier.graphUri());
        }

        // set new UUID and remove concrete stereotype
        var filteredStereotypes = CIMStereotypes.withoutConcrete(classCopy.getStereotypes());
        classCopy.setUuid(UUID.randomUUID());
        classCopy.setStereotypes(filteredStereotypes);

        return classCopy;
    }

    private List<CIMClass> fetchStubbedSuperClasses(Graph graph, UUID classUUID) {
        var model = ModelFactory.createModelForGraph(graph);
        var relationFinder = new CIMClassRelationFinder(model);

        var superClasses = relationFinder.findSuperClasses(classUUID);
        for (var superClass : superClasses) {
            // set new UUID and remove concrete stereotype
            var filteredStereotypes = CIMStereotypes.withoutConcrete(superClass.getStereotypes());
            superClass.setUuid(UUID.randomUUID());
            superClass.setStereotypes(filteredStereotypes);
        }

        return superClasses.stream().toList();
    }

    /**
     * Inserts the stubs that are not defined in the target graph yet, keeping the package they
     * belong to in their source graph. A package that does not exist in the target graph stays a
     * referenced only resource there, just like the superclass of a stub inserted without its
     * inheritance chain.
     *
     * @return the uris of the classes that were inserted
     */
    private Set<String> insertStubs(
            GraphIdentifier newGraphIdentifier, Collection<CIMClass> stubs) {
        var insertedUris = new LinkedHashSet<String>();
        try (var ctx =
                databasePort.getGraphWithContext(newGraphIdentifier).begin(ReadWrite.WRITE)) {
            var newGraph = ctx.getRdfGraph();
            var labels = new ArrayList<String>();
            for (var stub : stubs) {
                if (CIMResourceUtils.containsClass(newGraph, stub.getUri())) {
                    continue;
                }
                CIMUpdates.insertClass(newGraph, newGraph.getPrefixMapping(), stub);
                insertedUris.add(stub.getUri().toString());
                labels.add(stub.getLabel().getValue());
            }
            if (insertedUris.isEmpty()) {
                return insertedUris;
            }
            ctx.commit(
                    "Added "
                            + String.join(", ", labels)
                            + " to graph "
                            + newGraphIdentifier.graphUri());
        }
        return insertedUris;
    }

    /**
     * Reads the identifiers the stubs have in the target graph, keyed by class uri. They can differ
     * from the ones of the source graph, because a class keeps the uuid it was already referenced
     * with in the target graph.
     */
    private Map<String, TargetIdentifiers> readTargetIdentifiers(
            GraphIdentifier newGraphIdentifier, Collection<CIMClass> stubs) {
        var identifiers = new LinkedHashMap<String, TargetIdentifiers>();
        try (var ctx = databasePort.getGraphWithContext(newGraphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            for (var stub : stubs) {
                var uri = stub.getUri().toString();
                var target =
                        org.rdfarchitect.models.cim.data.dto.facade.CIMClass.fromResource(
                                newGraphIdentifier.graphUri(), model, model.getResource(uri));
                identifiers.put(
                        uri,
                        new TargetIdentifiers(
                                uuidOf(target), uuidOf(target.getBelongsToCategory())));
            }
        }
        return identifiers;
    }

    private UUID uuidOf(ICIMResource resource) {
        return resource == null ? null : resource.getUuid();
    }

    private record TargetIdentifiers(UUID classUUID, UUID packageUUID) {}
}
