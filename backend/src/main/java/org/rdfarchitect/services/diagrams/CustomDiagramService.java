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
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.SuperClassDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.ClassSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramColorDataDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.GraphSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.MergedClassDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.select.GetClassListUseCase;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                RemoveFromDiagramUseCase,
                CrossProfileColorUseCase {

    private final DatabasePort databasePort;
    private final GetClassListUseCase getClassListUseCase;

    @Override
    public List<CustomDiagram> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ctx.getCustomDiagrams().values().stream().toList();
        }
    }

    @Override
    public List<CustomDiagram> getCustomDiagramsForDataset(String datasetName) {
        return databasePort.getDatasetDiagrams(datasetName).values().stream().toList();
    }

    @Override
    public CrossProfileDiagramDTO getCrossProfileDiagram(
            String datasetName, boolean includeProperties, boolean doLayout) {
        var graphUris = databasePort.listGraphUris(datasetName);
        var crossProfileDiagramInfo = databasePort.getCrossProfileDiagramInfo(datasetName);
        var crossProfileDiagramUUID = crossProfileDiagramInfo.getCrossProfileDiagramUUID();
        var diagramLayout = databasePort.getDatasetDiagramLayout(datasetName);

        Map<String, MergedClassDTO> mergeMap = new LinkedHashMap<>();

        for (var graphUri : graphUris) {
            var graphIdentifier = new GraphIdentifier(datasetName, graphUri);
            List<ClassUMLAdaptedDTO> classList;
            if (includeProperties) {
                classList = getClassListUseCase.getFullClassList(graphIdentifier);
            } else {
                classList = getClassListUseCase.getClassList(graphIdentifier, false);
            }
            var graphColor = crossProfileDiagramInfo.getColor(graphUri);

            for (var dto : classList) {
                var classUri = dto.getPrefix() + dto.getLabel();
                var mergedUuid = UUID.nameUUIDFromBytes(classUri.getBytes(StandardCharsets.UTF_8));

                var merged =
                        mergeMap.computeIfAbsent(
                                classUri,
                                uri ->
                                        MergedClassDTO.builder()
                                                .uuid(mergedUuid)
                                                .classUri(uri)
                                                .label(dto.getLabel())
                                                .build());

                merged.getSources().add(new ClassSourceDTO(dto.getUuid(), graphUri));

                if (includeProperties) {
                    mergeProperties(graphUri, dto, merged, graphColor);
                }
            }
            var attributeComparator =
                    Comparator.comparing(GraphSourceDTO<AttributeDTO>::getGraphUri)
                            .thenComparing(a -> a.getValue().getLabel().toLowerCase(Locale.ROOT));
            var enumEntryComparator =
                    Comparator.comparing(GraphSourceDTO<EnumEntryDTO>::getGraphUri)
                            .thenComparing(e -> e.getValue().getLabel().toLowerCase(Locale.ROOT));

            mergeMap.values()
                    .forEach(
                            merged -> {
                                merged.getAttributes().sort(attributeComparator);
                                merged.getEnumEntries().sort(enumEntryComparator);
                            });
        }
        if (doLayout) {
            doDiagramLayout(diagramLayout, crossProfileDiagramUUID, mergeMap);
        }
        return new CrossProfileDiagramDTO(
                crossProfileDiagramUUID, new ArrayList<>(mergeMap.values()));
    }

    private static void mergeProperties(
            String graphUri, ClassUMLAdaptedDTO dto, MergedClassDTO merged, String graphColor) {
        if (dto.getAttributes() != null) {
            mergeAttributes(graphUri, dto, merged, graphColor);
        }
        if (dto.getEnumEntries() != null) {
            mergeEnumEntries(graphUri, dto, merged, graphColor);
        }
        if (dto.getAssociationPairs() != null) {
            mergeAssociationPairs(graphUri, dto, merged, graphColor);
        }
        if (dto.getSuperClass() != null) {
            var superClass = dto.getSuperClass();
            merged.getSuperClasses()
                    .add(
                            new GraphSourceDTO<>(
                                    graphUri,
                                    graphColor,
                                    new SuperClassDTO(
                                            superClass.getPrefix(), superClass.getLabel())));
        }
        if (dto.getStereotypes() != null) {
            mergeStereotypes(dto, merged);
        }
    }

    private static void mergeAttributes(
            String graphUri, ClassUMLAdaptedDTO dto, MergedClassDTO merged, String graphColor) {
        dto.getAttributes()
                .forEach(
                        attr ->
                                merged.getAttributes()
                                        .add(new GraphSourceDTO<>(graphUri, graphColor, attr)));
    }

    private static void mergeEnumEntries(
            String graphUri, ClassUMLAdaptedDTO dto, MergedClassDTO merged, String graphColor) {
        dto.getEnumEntries()
                .forEach(
                        entry ->
                                merged.getEnumEntries()
                                        .add(new GraphSourceDTO<>(graphUri, graphColor, entry)));
    }

    private static void mergeAssociationPairs(
            String graphUri, ClassUMLAdaptedDTO dto, MergedClassDTO merged, String graphColor) {
        dto.getAssociationPairs()
                .forEach(
                        assoc ->
                                merged.getAssociationPairs()
                                        .add(new GraphSourceDTO<>(graphUri, graphColor, assoc)));
    }

    private static void mergeStereotypes(ClassUMLAdaptedDTO dto, MergedClassDTO merged) {
        dto.getStereotypes()
                .forEach(
                        stereotype -> {
                            if (!merged.getStereotypes().contains(new CIMSStereotype(stereotype))) {
                                merged.getStereotypes().add(new CIMSStereotype(stereotype));
                            }
                        });
    }

    private static void doDiagramLayout(
            DiagramLayout diagramLayout,
            UUID crossProfileDiagramUUID,
            Map<String, MergedClassDTO> mergeMap) {

        var model = diagramLayout.getDiagramLayoutModel();
        if (DLObjectFetcher.fetchDiagram(model, crossProfileDiagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(
                    model, crossProfileDiagramUUID, "CrossProfileDiagram");
        }
        var existingDOs = DLObjectFetcher.fetchDiagramDOs(model, new MRID(crossProfileDiagramUUID));
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
    public void deleteCustomDiagram(String datasetName, String diagramId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        diagrams.remove(UUID.fromString(diagramId));
    }

    @Override
    public void replaceCustomDiagram(String datasetName, String diagramId, CustomDiagram diagram) {
        if (!Objects.equals(diagramId, diagram.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagram.getDiagramId()
                            + "'");
        }
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        diagrams.put(UUID.fromString(diagramId), diagram);
    }

    @Override
    public void removeFromDiagram(String datasetName, String diagramId, UUID classId) {
        var diagrams = databasePort.getDatasetDiagrams(datasetName);
        var diagram = diagrams.get(UUID.fromString(diagramId));
        if (diagram != null) {
            var classes = diagram.getClasses();
            classes.removeIf(c -> c.getUuid().equals(classId));
            diagram.setClasses(classes);
        }
    }

    @Override
    public void deleteCustomDiagram(GraphIdentifier graphIdentifier, String diagramId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams().remove(UUID.fromString(diagramId));
            ctx.commit("deleted diagram %s".formatted(diagramId));
        }
    }

    @Override
    public void replaceCustomDiagram(
            GraphIdentifier graphIdentifier, String diagramId, CustomDiagram diagram) {
        if (!Objects.equals(diagramId, diagram.getDiagramId().toString())) {
            throw new IllegalArgumentException(
                    "Diagram ID mismatch: URL parameter '"
                            + diagramId
                            + "' does not match diagram object ID '"
                            + diagram.getDiagramId()
                            + "'");
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams().put(UUID.fromString(diagramId), diagram);
            ctx.commit("replaced diagram %s".formatted(diagramId));
        }
    }

    @Override
    public void removeFromDiagram(GraphIdentifier graphIdentifier, String diagramId, UUID classId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagram = ctx.getCustomDiagrams().get(UUID.fromString(diagramId));
            if (diagram != null) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            ctx.commit("removed class %s from diagram %s".formatted(classId, diagramId));
        }
    }

    @Override
    public void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            for (var diagram : ctx.getCustomDiagrams().values()) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            for (var diagram :
                    databasePort.getDatasetDiagrams(graphIdentifier.datasetName()).values()) {
                var classes = diagram.getClasses();
                classes.removeIf(c -> c.getUuid().equals(classId));
                diagram.setClasses(classes);
            }
            ctx.commit("removed class %s from all diagrams".formatted(classId));
        }
    }

    @Override
    public CrossProfileDiagramColorDataDTO getCrossProfileColors(String datasetName) {
        var graphUris = databasePort.listGraphUris(datasetName);
        var colorsDTO = new CrossProfileDiagramColorDataDTO(new HashMap<>());
        for (var graphUri : graphUris) {
            var graphColor =
                    databasePort.getCrossProfileDiagramInfo(datasetName).getColor(graphUri);
            colorsDTO.getGraphColors().put(graphUri, graphColor);
        }
        return colorsDTO;
    }

    @Override
    public void replaceCrossProfileColors(String datasetName, CrossProfileDiagramColorDataDTO dto) {
        var graphUris = databasePort.listGraphUris(datasetName);
        for (var graphUri : graphUris) {
            if (dto.getGraphColors().containsKey(graphUri)) {
                databasePort
                        .getCrossProfileDiagramInfo(datasetName)
                        .setColor(graphUri, dto.getGraphColors().get(graphUri));
            }
        }
    }
}
