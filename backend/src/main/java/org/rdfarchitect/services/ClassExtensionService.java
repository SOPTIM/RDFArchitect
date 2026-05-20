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
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ClassExtensionService implements ClassExtensionUseCase {

    private DatabasePort databasePort;
    private ClassMapper classMapper;
    private ChangeLogUseCase changeLogUseCase;

    @Override
    public ClassDTO extendClass(
            GraphIdentifier graphIdentifier, String classUUID, GraphIdentifier newGraphIdentifier) {
        var graph = databasePort.getGraphWithContext(graphIdentifier);
        CIMClass classCopy;
        List<CIMClass> superClasses;

        GraphRewindable rdfGraph = null;
        try {
            rdfGraph = graph.getRdfGraph();
            rdfGraph.begin(TxnType.READ);

            classCopy = fetchStubbedClassCopy(graphIdentifier, classUUID);
            superClasses = fetchStubbedSuperClasses(rdfGraph, UUID.fromString(classUUID));

        } finally {
            if (rdfGraph != null) {
                rdfGraph.end();
            }
        }

        var newGraph = databasePort.getGraphWithContext(newGraphIdentifier).getRdfGraph();
        insertNewClasses(newGraph, newGraphIdentifier, classCopy, superClasses);
        changeLogUseCase.recordChange(
                newGraphIdentifier,
                new ChangeLogEntry(
                        "Added "
                                + classCopy.getLabel().getValue()
                                + " and its superclasses to graph "
                                + newGraphIdentifier.graphUri(),
                        newGraph.getLastDelta()));

        return classMapper.toDTO(classCopy);
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
        var filteredStereotypes =
                classCopy.getStereotypes().stream()
                        .filter(s -> !s.getStereotype().equals(CIMStereotypes.concreteString))
                        .toList();
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
            var filteredStereotypes =
                    superClass.getStereotypes().stream()
                            .filter(s -> !s.getStereotype().equals(CIMStereotypes.concreteString))
                            .toList();
            superClass.setUuid(UUID.randomUUID());
            superClass.setStereotypes(filteredStereotypes);
        }

        return superClasses.stream().toList();
    }

    private void insertNewClasses(
            GraphRewindable newGraph,
            GraphIdentifier newGraphIdentifier,
            CIMClass classCopy,
            List<CIMClass> superClasses) {
        try {
            var model = ModelFactory.createModelForGraph(newGraph);
            newGraph.begin(TxnType.WRITE);
            var prefixMapping = databasePort.getPrefixMapping(newGraphIdentifier.datasetName());

            var newPackage = fetchNewPackage(model);

            for (var cls : superClasses) {
                if (!model.containsResource(model.createResource(cls.getUri().toString()))) {
                    cls.setBelongsToCategory(newPackage);
                    CIMUpdates.insertClass(newGraph, prefixMapping, cls);
                }
            }
            classCopy.setBelongsToCategory(newPackage);
            CIMUpdates.insertClass(newGraph, prefixMapping, classCopy);

            newGraph.commit();
        } finally {
            if (newGraph != null) {
                newGraph.end();
            }
        }
    }

    private CIMSBelongsToCategory fetchNewPackage(Model model) {
        var it =
                model.listSubjectsWithProperty(RDF.type, CIMS.classCategory)
                        .filterKeep(s -> s.toString().contains("Core"));

        if (!it.hasNext()) {
            return null;
        }

        var corePackage = it.next();
        CIMSBelongsToCategory pack = new CIMSBelongsToCategory();
        pack.setUri(new URI(corePackage.getURI()));
        pack.setLabel(new RDFSLabel(corePackage.getProperty(RDFS.label).getString(), "en"));
        pack.setUuid(UUID.fromString(corePackage.getProperty(RDFA.uuid).getString()));
        return pack;
    }
}
