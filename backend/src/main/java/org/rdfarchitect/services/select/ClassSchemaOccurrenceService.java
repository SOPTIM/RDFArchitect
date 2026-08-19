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

package org.rdfarchitect.services.select;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.rdfarchitect.api.dto.ClassSchemaOccurrenceDTO;
import org.rdfarchitect.api.dto.ClassStubDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassSchemaOccurrenceService implements ListClassSchemaOccurrencesUseCase {

    private final DatabasePort databasePort;
    private final ListGraphsUseCase listGraphsUseCase;

    @Override
    public List<ClassSchemaOccurrenceDTO> listSchemaOccurrences(
            GraphIdentifier graphIdentifier, String classUUID) {
        var datasetName = graphIdentifier.datasetName();
        var classUri = findClassUri(graphIdentifier, classUUID);
        var keywords = CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName);

        var occurrences = new ArrayList<ClassSchemaOccurrenceDTO>();
        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            occurrences.add(occurrenceIn(datasetName, graphUri, keywords.get(graphUri), classUri));
        }
        return occurrences;
    }

    private String findClassUri(GraphIdentifier graphIdentifier, String classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var subjects = model.listSubjectsWithProperty(RDFA.uuid, classUUID).toList();
            var resource =
                    subjects.stream()
                            .filter(RDFNode::isURIResource)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Class with UUID "
                                                            + classUUID
                                                            + " not found in graph "
                                                            + graphIdentifier.graphUri()));
            return resource.getURI();
        }
    }

    private ClassSchemaOccurrenceDTO occurrenceIn(
            String datasetName, String graphUri, String keyword, String classUri) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            var model = ModelFactory.createModelForGraph(graph);
            var resource = model.getResource(classUri);
            if (!CIMResourceUtils.containsClass(graph, new URI(classUri))
                    || !resource.hasProperty(RDFA.uuid)) {
                return new ClassSchemaOccurrenceDTO(graphUri, keyword, false, null, null);
            }
            return new ClassSchemaOccurrenceDTO(
                    graphUri,
                    keyword,
                    true,
                    CIMResourceUtils.findUuidForResource(resource),
                    stubOf(new CIMClass(graphUri, model, resource)));
        }
    }

    /** The data an extension into another schema copies from this class. */
    private ClassStubDTO stubOf(CIMClass cimClass) {
        var comment = cimClass.getComment();
        var superClass = cimClass.getSuperClasses().stream().findFirst().orElse(null);
        var classCategory = cimClass.getBelongsToCategory();
        return new ClassStubDTO(
                cimClass.getLabel().getValue(),
                comment == null ? null : comment.getValue(),
                superClass == null ? null : superClass.getUri().toString(),
                classCategory == null ? null : classCategory.getUri().toString(),
                classCategory == null ? null : classCategory.getLabel().getValue(),
                CIMStereotypes.withoutConcrete(cimClass.getStereotypes()).stream()
                        .map(CIMSStereotype::getStereotype)
                        .sorted()
                        .toList());
    }
}
