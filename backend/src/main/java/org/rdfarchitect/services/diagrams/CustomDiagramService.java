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
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.CustomDiagramDTO;
import org.rdfarchitect.api.dto.GraphDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.ClassSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramColorDataDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.MergedClassDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.DiagramLayout;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.services.ChangeDescriptions;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.dl.update.SyncDiagramLayoutUseCase;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeLayoutSync;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeResolver;
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
    private final SyncDiagramLayoutUseCase syncDiagramLayoutUseCase;

    @Override
    public List<CustomDiagramDTO> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphName());
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
    public List<CustomDiagramDTO> getCustomDiagramsForDataset(String workspaceName) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
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
    public UUID getCrossProfileDiagramId(String workspaceName) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            return transaction.crossProfileDiagramId();
        }
    }

    @Override
    public CrossProfileDiagramDTO getCrossProfileDiagram(String workspaceName, boolean doLayout) {
        return getCrossProfileDiagram(workspaceName, doLayout, loadProfiles(workspaceName));
    }

    private List<CIMProfileModel> loadProfiles(String workspaceName) {
        return CIMProfileModels.loadAll(
                databasePort,
                CIMProfileModels.keywordsByGraphName(listGraphsUseCase, workspaceName),
                workspaceName,
                null);
    }

    @Override
    public CrossProfileDiagramDTO getCrossProfileDiagram(
            String workspaceName, boolean doLayout, List<CIMProfileModel> profiles) {
        var graphDTOsMap =
                listGraphsUseCase.listGraphs(workspaceName).stream()
                        .collect(Collectors.toMap(g -> g.getUri().toString(), g -> g));
        // Merging the profiles is the expensive part and only reads, so it runs under a read
        // lock; the layout is inserted afterwards under a write lock. Inserting is idempotent, so
        // the gap between the two does no harm.
        UUID crossProfileDiagramUUID;
        Map<String, MergedClassDTO> mergeMap;
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            crossProfileDiagramUUID = transaction.crossProfileDiagramId();
            mergeMap = mergeProfiles(profiles, graphDTOsMap);
        }
        if (doLayout) {
            try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
                doDiagramLayout(transaction.layout(), crossProfileDiagramUUID, mergeMap, profiles);
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
            var graphName = profile.graphName();

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
                        .add(new ClassSourceDTO(cimClass.getUuid(), graphDTOsMap.get(graphName)));
            }
        }
        return mergeMap;
    }

    private static void doDiagramLayout(
            DiagramLayout diagramLayout,
            UUID crossProfileDiagramUUID,
            Map<String, MergedClassDTO> mergeMap,
            List<CIMProfileModel> profiles) {

        insertCrossProfileClassLayoutData(
                diagramLayout.getDiagramLayoutModel(), crossProfileDiagramUUID, mergeMap, profiles);
    }

    private static void insertCrossProfileClassLayoutData(
            Model model,
            UUID crossProfileDiagramUUID,
            Map<String, MergedClassDTO> mergeMap,
            List<CIMProfileModel> profiles) {
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

        var createdClassLayoutData = false;
        for (var merged : mergeMap.values()) {
            if (!existingClassUUIDs.contains(merged.getUuid())) {
                DiagramLayoutServiceUtils.insertClassLayoutData(
                        model,
                        crossProfileDiagramUUID,
                        merged.getClassUri(),
                        merged.getUuid(),
                        0,
                        0);
                createdClassLayoutData = true;
            }
        }
        if (createdClassLayoutData) {
            EdgeLayoutSync.syncEdges(
                    model, crossProfileDiagramUUID, EdgeResolver.forMergedProfiles(profiles));
        }
    }

    @Override
    public void deleteCustomDatasetDiagram(String workspaceName, String diagramId) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            var removed = transaction.diagrams().remove(UUID.fromString(diagramId));
            syncDiagramLayoutUseCase.syncDatasetDiagramLayout(workspaceName);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Deleted", "diagram", ChangeDescriptions.diagramName(removed)));
        }
    }

    @Override
    public void replaceCustomDatasetDiagram(
            String workspaceName, String diagramId, CustomDiagramDTO diagramDTO) {
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
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            transaction.diagrams().put(UUID.fromString(diagramId), diagram);
            syncDiagramLayoutUseCase.syncDatasetDiagramLayout(workspaceName);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Updated", "diagram", ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromCustomDatasetDiagram(
            String workspaceName, String diagramId, UUID classId) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            var diagram = transaction.diagrams().get(UUID.fromString(diagramId));
            var className = classNameIn(transaction, diagram, classId);
            if (diagram != null) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            syncDiagramLayoutUseCase.syncDatasetDiagramLayout(workspaceName);
            // Reading the diagrams enrolled them, so the transaction has to be committed either
            // way; a commit that changed nothing records nothing.
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            ChangeDescriptions.actionOn("Removed", "class", className) + " from",
                            "diagram",
                            ChangeDescriptions.diagramName(diagram)));
        }
    }

    /**
     * The label of a class on a workspace diagram. Such a diagram draws from several graphs, so the
     * entry that is about to go says which graph to ask; one that is already gone leaves the change
     * unnamed rather than identified.
     */
    private static String classNameIn(
            WorkspaceTransaction transaction, CustomDiagram diagram, UUID classId) {
        if (diagram == null) {
            return "";
        }
        return diagram.getClasses().stream()
                .filter(cls -> cls.getUuid().equals(classId) && cls.getSchemaName() != null)
                .findFirst()
                .map(cls -> classNameInGraph(transaction, cls.getSchemaName().toString(), classId))
                .orElse("");
    }

    private static String classNameInGraph(
            WorkspaceTransaction transaction, String graphName, UUID classId) {
        if (!transaction.graphNames().contains(graphName)) {
            return "";
        }
        return ChangeDescriptions.className(transaction.graph(graphName).getRdfGraph(), classId);
    }

    @Override
    public void deleteCustomGraphDiagram(GraphIdentifier graphIdentifier, String diagramId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphName());
            var removed = ctx.getCustomDiagrams().remove(UUID.fromString(diagramId));
            syncDiagramLayoutUseCase.syncDiagramLayout(graphIdentifier);
            transaction.commit(
                    ChangeDescriptions.actionOn(
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
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphName());
            // dto is necessary for swagger to infer the correct type, but we need to create a new
            // CustomDiagram object to store in the database
            var diagram =
                    new CustomDiagram(
                            diagramDTO.getDiagramId(),
                            diagramDTO.getName(),
                            diagramDTO.getClasses());
            ctx.getCustomDiagrams().put(UUID.fromString(diagramId), diagram);
            syncDiagramLayoutUseCase.syncDiagramLayout(graphIdentifier);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Updated", "diagram", ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromCustomGraphDiagram(
            GraphIdentifier graphIdentifier, String diagramId, UUID classId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphName());
            var diagram = ctx.getCustomDiagrams().get(UUID.fromString(diagramId));
            var className = ChangeDescriptions.className(ctx.getRdfGraph(), classId);
            if (diagram != null) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            syncDiagramLayoutUseCase.syncDiagramLayout(graphIdentifier);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            ChangeDescriptions.actionOn("Removed", "class", className) + " from",
                            "diagram",
                            ChangeDescriptions.diagramName(diagram)));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.workspaceName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphName());
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
    public CrossProfileDiagramColorDataDTO getCrossProfileColors(String workspaceName) {
        var colorsDTO = new CrossProfileDiagramColorDataDTO(new HashMap<>());
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            for (var graphName : transaction.graphNames()) {
                colorsDTO
                        .getSchemaColors()
                        .put(graphName, transaction.crossProfileColors().getColor(graphName));
            }
        }
        return colorsDTO;
    }

    @Override
    public void replaceCrossProfileColors(
            String workspaceName, CrossProfileDiagramColorDataDTO dto) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            for (var graphName : transaction.graphNames()) {
                if (dto.getSchemaColors().containsKey(graphName)) {
                    transaction
                            .crossProfileColors()
                            .setColor(graphName, dto.getSchemaColors().get(graphName));
                }
            }
            transaction.commit("Changed the cross profile colours");
        }
    }
}
