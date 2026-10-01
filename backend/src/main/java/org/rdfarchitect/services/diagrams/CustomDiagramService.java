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

package org.rdfarchitect.services.diagrams;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.CustomDiagramDTO;
import org.rdfarchitect.api.dto.GraphDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.ClassSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramColorDataDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.MergedClassDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.services.ChangeDescriptions;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.select.ListGraphsUseCase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomDiagramService
        implements GetCustomDiagramsUseCase,
                ReplaceCustomDiagramUseCase,
                DeleteCustomDiagramUseCase,
                RemoveFromCustomDiagramUseCase,
                CrossProfileColorUseCase {

    private final DatabasePort databasePort;
    private final ListGraphsUseCase listGraphsUseCase;

    @Override
    public List<CustomDiagramDTO> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            return ctx.getCustomDiagrams().values().stream()
                    .map(
                            diagram ->
                                    new CustomDiagramDTO(
                                            diagram.getDiagramId(),
                                            diagram.getName(),
                                            diagram.getClasses()))
                    .toList();
        }
    }

    @Override
    public List<CustomDiagramDTO> getCustomDiagramsForDataset(String datasetName) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.READ)) {
            return transaction.diagrams().values().stream()
                    .map(
                            diagram ->
                                    new CustomDiagramDTO(
                                            diagram.getDiagramId(),
                                            diagram.getName(),
                                            diagram.getClasses()))
                    .toList();
        }
    }

    @Override
    public CrossProfileDiagramDTO getCrossProfileDiagram(String datasetName, boolean doLayout) {
        return getCrossProfileDiagram(datasetName, doLayout, loadProfiles(datasetName));
    }

    private List<CIMProfileModel> loadProfiles(String datasetName) {
        return CIMProfileModels.loadAll(
                databasePort,
                CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName),
                datasetName,
                null);
    }

    @Override
    public CrossProfileDiagramDTO getCrossProfileDiagram(
            String datasetName, boolean doLayout, List<CIMProfileModel> profiles) {
        var graphDTOsMap =
                listGraphsUseCase.listGraphs(datasetName).stream()
                        .collect(Collectors.toMap(g -> g.getUri().toString(), g -> g));
        // Merging the profiles is the expensive part and only reads, so it runs under a read
        // lock; the layout is inserted afterwards under a write lock. Inserting is idempotent, so
        // the gap between the two does no harm.
        UUID crossProfileDiagramUUID;
        Map<String, MergedClassDTO> mergeMap;
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.READ)) {
            crossProfileDiagramUUID = transaction.crossProfileInfo().getCrossProfileDiagramUUID();
            mergeMap = mergeProfiles(profiles, graphDTOsMap);
        }
        if (doLayout) {
            try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
                doDiagramLayout(transaction.layout(), crossProfileDiagramUUID, mergeMap);
                // Looking at the diagram is not a change the user's next undo should take back.
                transaction.commitWithoutHistory();
            }
        }
        return new CrossProfileDiagramDTO(
                crossProfileDiagramUUID, new ArrayList<>(mergeMap.values()));
    }

    private Map<String, MergedClassDTO> mergeProfiles(
            List<CIMProfileModel> profiles, Map<String, GraphDTO> graphDTOsMap) {
        Map<String, MergedClassDTO> mergeMap = new LinkedHashMap<>();

        for (var profile : profiles) {
            var graphUri = profile.graphUri();

            for (var cimClass : profile.model().getCIMClasses()) {
                var classUri = cimClass.getUri().toString();
                var mergedUuid = CrossProfileUtils.mergedUuid(classUri);

                var merged =
                        mergeMap.computeIfAbsent(
                                classUri,
                                uri ->
                                        MergedClassDTO.builder()
                                                .uuid(mergedUuid)
                                                .classUri(uri)
                                                .label(cimClass.getLabel().getValue())
                                                .build());

                merged.getSources()
                        .add(new ClassSourceDTO(cimClass.getUuid(), graphDTOsMap.get(graphUri)));
            }
        }
        return mergeMap;
    }

    private static void doDiagramLayout(
            DiagramLayoutDelta diagramLayout,
            UUID crossProfileDiagramUUID,
            Map<String, MergedClassDTO> mergeMap) {

        var model = diagramLayout.getDiagramLayoutModel();
        DiagramLayoutServiceUtils.insertAllDiagramObjectStyles(model);
        if (DLObjectFetcher.fetchDiagram(model, crossProfileDiagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(
                    model, crossProfileDiagramUUID, "CrossProfileDiagram");
        }
        var existingDOs =
                DLObjectFetcher.fetchDiagramClassDOs(model, new MRID(crossProfileDiagramUUID));
        var existingClassUUIDs =
                existingDOs.stream()
                        .map(DiagramObject::getBelongsToIdentifiedObject)
                        .map(MRID::getUuid)
                        .collect(Collectors.toSet());

        for (var merged : mergeMap.values()) {
            if (!existingClassUUIDs.contains(merged.getUuid())) {
                var doMRID =
                        DiagramLayoutServiceUtils.insertDiagramObject(
                                model,
                                crossProfileDiagramUUID,
                                merged.getClassUri(),
                                merged.getUuid());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                        model, crossProfileDiagramUUID, doMRID);
            }
        }
    }

    @Override
    public void deleteCustomDatasetDiagram(String datasetName, String diagramId) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var removed = transaction.diagrams().remove(UUID.fromString(diagramId));
            transaction.commit(
                    ChangeDescriptions.in(
                            "Deleted", "diagram", ChangeDescriptions.diagramName(removed)));
        }
    }

    @Override
    public void replaceCustomDatasetDiagram(
            String datasetName, String diagramId, CustomDiagramDTO diagramDTO) {
        if (!Objects.equals(diagramId, diagramDTO.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagramDTO.getDiagramId()
                            + "'");
        }
        // dto is necessary for swagger to infer the correct type, but we need to create a new
        // CustomDiagram object to store in the database
        var diagram =
                new CustomDiagram(
                        diagramDTO.getDiagramId(), diagramDTO.getName(), diagramDTO.getClasses());
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            transaction.diagrams().put(UUID.fromString(diagramId), diagram);
            transaction.commit(
                    ChangeDescriptions.in(
                            "Updated", "diagram", ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromCustomDatasetDiagram(String datasetName, String diagramId, UUID classId) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var diagram = transaction.diagrams().get(UUID.fromString(diagramId));
            if (diagram != null) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            // Reading the diagrams enrolled them, so the transaction has to be committed either
            // way; a commit that changed nothing records nothing.
            transaction.commit(
                    ChangeDescriptions.in(
                            "Removed a class from",
                            "diagram",
                            ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void deleteCustomGraphDiagram(GraphIdentifier graphIdentifier, String diagramId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var removed = ctx.getCustomDiagrams().remove(UUID.fromString(diagramId));
            transaction.commit(
                    ChangeDescriptions.in(
                            "Deleted", "diagram", ChangeDescriptions.diagramName(removed)));
        }
    }

    @Override
    public void replaceCustomGraphDiagram(
            GraphIdentifier graphIdentifier, String diagramId, CustomDiagramDTO diagramDTO) {
        if (!Objects.equals(diagramId, diagramDTO.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagramDTO.getDiagramId()
                            + "'");
        }
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            // dto is necessary for swagger to infer the correct type, but we need to create a new
            // CustomDiagram object to store in the database
            var diagram =
                    new CustomDiagram(
                            diagramDTO.getDiagramId(),
                            diagramDTO.getName(),
                            diagramDTO.getClasses());
            ctx.getCustomDiagrams().put(UUID.fromString(diagramId), diagram);
            transaction.commit(
                    ChangeDescriptions.in(
                            "Updated", "diagram", ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromCustomGraphDiagram(
            GraphIdentifier graphIdentifier, String diagramId, UUID classId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagram = ctx.getCustomDiagrams().get(UUID.fromString(diagramId));
            if (diagram != null) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            transaction.commit(
                    ChangeDescriptions.in(
                            "Removed a class from",
                            "diagram",
                            ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            for (var diagram : ctx.getCustomDiagrams().values()) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            for (var diagram : transaction.diagrams().values()) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            transaction.commit("Removed a class from all diagrams");
        }
    }

    @Override
    public CrossProfileDiagramColorDataDTO getCrossProfileColors(String datasetName) {
        var colorsDTO = new CrossProfileDiagramColorDataDTO(new HashMap<>());
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.READ)) {
            for (var graphUri : transaction.graphUris()) {
                colorsDTO
                        .getGraphColors()
                        .put(graphUri, transaction.crossProfileInfo().getColor(graphUri));
            }
        }
        return colorsDTO;
    }

    @Override
    public void replaceCrossProfileColors(String datasetName, CrossProfileDiagramColorDataDTO dto) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            for (var graphUri : transaction.graphUris()) {
                if (dto.getGraphColors().containsKey(graphUri)) {
                    transaction
                            .crossProfileInfo()
                            .setColor(graphUri, dto.getGraphColors().get(graphUri));
                }
            }
            transaction.commit("Changed the cross profile colours");
        }
    }
}
