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

package org.rdfarchitect.services.update.graph;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.xmi.map.XmiDiagrams.ImportedDiagram;
import org.rdfarchitect.xmi.map.XmiDiagrams.PlacedClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Stores the diagrams of an export as custom diagrams.
 *
 * <p>Which kind of custom diagram one becomes is decided by what it draws rather than by where it
 * was declared. A diagram all of whose classes ended up in a single schema belongs to that schema;
 * one drawing from several belongs to the workspace, because no single schema can hold it. The
 * export says nothing about this either way — the same Enterprise Architect diagram is one or the
 * other depending only on which profiles were chosen alongside it.
 *
 * <p>Both kinds carry positions, and the two address a class differently: a schema diagram by the
 * uuid of the class, a workspace diagram by the merged uuid its uri hashes to, which is how the
 * workspace-wide layout identifies the same class across schemas.
 */
@Service
@RequiredArgsConstructor
class XmiDiagramStore {

    private static final Logger logger = LoggerFactory.getLogger(XmiDiagramStore.class);

    private final DatabasePort databasePort;

    /**
     * Stores every diagram, each where it belongs.
     *
     * @param cancelled asked before every diagram, because this is the longest part of an import
     *     and a cancellation that is only noticed after it would not look like one
     * @return how many were stored
     */
    int store(String datasetName, List<ImportedDiagram> diagrams, BooleanSupplier cancelled) {
        var stored = 0;
        for (var diagram : diagrams) {
            if (cancelled.getAsBoolean()) {
                logger.info("Stopped after {} diagram(s): the import was cancelled.", stored);
                return stored;
            }
            try {
                storeOne(datasetName, diagram);
                stored++;
            } catch (RuntimeException exception) {
                // A diagram that cannot be stored costs its positions, not the schemas it draws.
                logger.warn(
                        "Unable to store the diagram '{}': {}",
                        diagram.name(),
                        exception.getMessage());
            }
        }
        return stored;
    }

    private void storeOne(String datasetName, ImportedDiagram diagram) {
        var schemaUri = diagram.schemaUri();
        if (schemaUri != null) {
            storeInGraph(new GraphIdentifier(datasetName, schemaUri), diagram);
        } else {
            storeInDataset(datasetName, diagram);
        }
    }

    private void storeInGraph(GraphIdentifier graphIdentifier, ImportedDiagram diagram) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams()
                    .put(
                            diagram.id(),
                            new CustomDiagram(diagram.id(), diagram.name(), classesIn(diagram)));
            writeLayout(
                    ctx.getDiagramLayout().getDiagramLayoutModel(),
                    diagram,
                    placed -> placed.location().uuid());
            ctx.commit("imported diagram %s".formatted(diagram.name()));
        }
    }

    private void storeInDataset(String datasetName, ImportedDiagram diagram) {
        databasePort
                .getDatasetDiagrams(datasetName)
                .put(
                        diagram.id(),
                        new CustomDiagram(diagram.id(), diagram.name(), classesIn(diagram)));
        writeLayout(
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel(),
                diagram,
                placed -> CrossProfileUtils.mergedUuid(placed.location().classUri()));
    }

    /**
     * Writes the diagram and a positioned object per class, in the order the export draws them,
     * which is the z-order.
     *
     * <p>The z is passed rather than left to be derived: deriving it reads every point of the
     * diagram per insert, and a schema whose diagrams hold thousands of them never finishes.
     */
    private void writeLayout(
            Model diagramLayoutModel,
            ImportedDiagram diagram,
            Function<PlacedClass, UUID> identityOf) {
        DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagram.id(), diagram.name());
        var z = 1;
        for (var placed : diagram.classes()) {
            var diagramObject =
                    DiagramLayoutServiceUtils.insertDiagramObject(
                            diagramLayoutModel,
                            diagram.id(),
                            placed.name(),
                            identityOf.apply(placed));
            DiagramLayoutServiceUtils.insertDiagramObjectPointAt(
                    diagramLayoutModel, diagramObject, placed.x(), placed.y(), z++);
        }
    }

    private List<ClassInDiagram> classesIn(ImportedDiagram diagram) {
        return diagram.classes().stream()
                .map(
                        placed ->
                                new ClassInDiagram(
                                        placed.location().uuid(),
                                        new URI(placed.location().graphUri())))
                .toList();
    }
}
