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
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassLocatorService implements LocateClassUseCase {

    private final DatabasePort databasePort;
    private final ListGraphsUseCase listGraphsUseCase;

    @Override
    public LocatedClass locate(String datasetName, String classUUID) {
        for (var graphUri : graphsInListingOrder(datasetName)) {
            var located = locateInGraph(datasetName, graphUri, classUUID);
            if (located != null) {
                return located;
            }
        }
        var merged = locateMergedClass(datasetName, classUUID);
        if (merged == null) {
            throw new IllegalArgumentException(
                    "Class with UUID " + classUUID + " not found in dataset " + datasetName);
        }
        return merged;
    }

    /**
     * The graphs in the order they are listed in, so that the result does not depend on storage.
     */
    private List<String> graphsInListingOrder(String datasetName) {
        var keywords = CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName);
        return databasePort.listGraphUris(datasetName).stream()
                .sorted(
                        Comparator.comparing(
                                        (String graphUri) -> shortName(keywords, graphUri),
                                        String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** The short name a schema is listed with, falling back to its uri. */
    private static String shortName(Map<String, String> keywords, String graphUri) {
        var keyword = keywords.get(graphUri);
        return keyword != null ? keyword : graphUri;
    }

    private LocatedClass locateInGraph(String datasetName, String graphUri, String classUUID) {
        var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            return model.listSubjectsWithProperty(RDFA.uuid, classUUID).toList().stream()
                    .filter(subject -> subject.isURIResource())
                    .findFirst()
                    .map(
                            subject ->
                                    new LocatedClass(
                                            graphUri, subject.getURI(), UUID.fromString(classUUID)))
                    .orElse(null);
        }
    }

    private LocatedClass locateMergedClass(String datasetName, String classUUID) {
        for (var graphUri : graphsInListingOrder(datasetName)) {
            var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
            try (var ctx =
                    databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
                var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
                for (var cimClass : new CIMModelFacade(graphUri, model).getCIMClasses()) {
                    var classUri = cimClass.getUri().toString();
                    if (CrossProfileUtils.mergedClassUuid(classUri).toString().equals(classUUID)) {
                        return new LocatedClass(graphUri, classUri, cimClass.getUuid());
                    }
                }
            }
        }
        return null;
    }
}
