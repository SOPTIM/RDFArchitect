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

import java.util.UUID;
import lombok.AllArgsConstructor;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.CIMClassRelationFinder;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ClassExtensionService implements ClassExtensionUseCase {

    private DatabasePort databasePort;
    private ClassMapper classMapper;

    @Override
    public ClassDTO extendClass(GraphIdentifier graphIdentifier, String classUUID, GraphIdentifier newGraphIdentifier) {
        var graph = databasePort.getGraphWithContext(graphIdentifier);
        var model = ModelFactory.createModelForGraph(graph.getRdfGraph());
        var cimObjectFetcher = new CIMObjectFetcher(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), graphIdentifier.graphUri(), databasePort.getPrefixMapping(graphIdentifier.datasetName()));

        var corePackage = model.listSubjectsWithProperty(RDF.type, CIMS.classCategory)
                .filterKeep(s -> s.toString().toLowerCase().contains("core"))
                .next();

        CIMSBelongsToCategory pack = null;
        if (corePackage != null) {
            pack = new CIMSBelongsToCategory();
            pack.setUri(new URI(corePackage.getURI()));
            pack.setLabel(new RDFSLabel(corePackage.getProperty(RDFS.label).getString()));
            pack.setUuid(UUID.fromString(corePackage.getProperty(RDFA.uuid).getString()));
        }

        var classResource = model.listSubjectsWithProperty(RDFA.uuid, classUUID).next();
        var cimClass = cimObjectFetcher.fetchCIMClass(
                classResource
                        .getProperty(RDFA.uuid)
                        .getObject()
                        .asLiteral()
                        .getString());
        var filteredStereotypes = cimClass.getStereotypes().stream()
                .filter(s -> !s.getStereotype().equals(CIMStereotypes.concreteString))
                .toList();
        cimClass.setUuid(UUID.randomUUID());
        cimClass.setStereotypes(filteredStereotypes);
        cimClass.setBelongsToCategory(pack);

        var relationFinder = new CIMClassRelationFinder(model);
        var superClasses = relationFinder.findSuperClasses(UUID.fromString(classUUID));
        for (var superClass : superClasses) {
            filteredStereotypes = superClass.getStereotypes().stream()
                    .filter(s -> !s.getStereotype().equals(CIMStereotypes.concreteString))
                    .toList();
            superClass.setUuid(UUID.randomUUID());
            superClass.setStereotypes(filteredStereotypes);
            superClass.setBelongsToCategory(pack);
        }

        GraphRewindableWithUUIDs newGraph = null;
        try {
            newGraph = databasePort.getGraphWithContext(newGraphIdentifier).getRdfGraph();
            newGraph.begin(TxnType.WRITE);
            for (var cls : superClasses) {
                CIMUpdates.insertClass(newGraph, databasePort.getPrefixMapping(newGraphIdentifier.datasetName()), cls);
            }
        } finally {
            if (newGraph != null) {
                newGraph.end();
            }
        }

        return classMapper.toDTO(cimClass);
    }
}
