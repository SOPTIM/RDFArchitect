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
    public LocatedClass locate(String workspaceName, String classUUID) {
        for (var schemaName : graphsInListingOrder(workspaceName)) {
            var located = locateInGraph(workspaceName, schemaName, classUUID);
            if (located != null) {
                return located;
            }
        }
        var merged = locateMergedClass(workspaceName, classUUID);
        if (merged == null) {
            throw new IllegalArgumentException(
                    "Class with UUID " + classUUID + " not found in dataset " + workspaceName);
        }
        return merged;
    }

    /**
     * The graphs in the order they are listed in, so that the result does not depend on storage.
     */
    private List<String> graphsInListingOrder(String workspaceName) {
        var keywords = CIMProfileModels.keywordsBySchemaName(listGraphsUseCase, workspaceName);
        return databasePort.listSchemaNames(workspaceName).stream()
                .sorted(
                        Comparator.comparing(
                                        (String schemaName) -> shortName(keywords, schemaName),
                                        String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** The short name a schema is listed with, falling back to its uri. */
    private static String shortName(Map<String, String> keywords, String schemaName) {
        var keyword = keywords.get(schemaName);
        return keyword != null ? keyword : schemaName;
    }

    private LocatedClass locateInGraph(String workspaceName, String schemaName, String classUUID) {
        var graphIdentifier = new GraphIdentifier(workspaceName, schemaName);
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.schemaName());
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            return model.listSubjectsWithProperty(RDFA.uuid, classUUID).toList().stream()
                    .filter(subject -> subject.isURIResource())
                    .findFirst()
                    .map(
                            subject ->
                                    new LocatedClass(
                                            schemaName,
                                            subject.getURI(),
                                            UUID.fromString(classUUID)))
                    .orElse(null);
        }
    }

    private LocatedClass locateMergedClass(String workspaceName, String classUUID) {
        for (var schemaName : graphsInListingOrder(workspaceName)) {
            var graphIdentifier = new GraphIdentifier(workspaceName, schemaName);
            try (var transaction =
                    databasePort.beginTransaction(
                            graphIdentifier.workspaceName(), ReadWrite.READ)) {
                var ctx = transaction.graph(graphIdentifier.schemaName());
                var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
                for (var cimClass : new CIMModelFacade(schemaName, model).getCIMClasses()) {
                    var classUri = cimClass.getUri().toString();
                    if (CrossProfileUtils.mergedUuid(classUri).toString().equals(classUUID)) {
                        return new LocatedClass(schemaName, classUri, cimClass.getUuid());
                    }
                }
            }
        }
        return null;
    }
}
