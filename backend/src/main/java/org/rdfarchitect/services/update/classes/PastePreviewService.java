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

package org.rdfarchitect.services.update.classes;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.PastePreviewRequestDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO.PasteReferenceDTO;
import org.rdfarchitect.api.dto.PastePreviewResponseDTO.PasteUsageDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PastePreviewService implements PastePreviewUseCase {

    private final DatabasePort databasePort;
    private final CopyClassSourceReader sourceReader;
    private final CopyClassReferenceResolver referenceResolver;

    @Override
    public PastePreviewResponseDTO previewPaste(
            PastePreviewRequestDTO previewRequest, GraphIdentifier targetGraphIdentifier) {

        var sources = sourceReader.toSources(previewRequest.getSources());
        if (sources.isEmpty()) {
            return PastePreviewResponseDTO.empty();
        }

        var sourceClasses = sourceReader.readSourceClasses(sources);
        var references = referenceResolver.resolve(sources, sourceClasses);
        var pastedUris = sourceClasses.stream().map(ICIMClass::getUri).collect(Collectors.toSet());

        var missingByKind =
                new EnumMap<CopyClassReference.Kind, List<CopyClassReference>>(
                        CopyClassReference.Kind.class);
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            for (var kind : CopyClassReference.Kind.values()) {
                missingByKind.put(kind, missing(references, kind, ctx.getRdfGraph(), pastedUris));
            }
        }

        var requires = requiredDataTypes(missingByKind.get(CopyClassReference.Kind.DATA_TYPE));
        var missing = new LinkedHashMap<String, List<PasteReferenceDTO>>();
        missingByKind.forEach(
                (kind, missingOfKind) ->
                        missing.put(
                                kind.name(),
                                toDTOs(
                                        missingOfKind,
                                        kind,
                                        kind.copiesMembers() ? requires : Map.of())));
        return new PastePreviewResponseDTO(missing);
    }

    private Map<URI, List<URI>> requiredDataTypes(List<CopyClassReference> missingDataTypes) {
        var listed =
                missingDataTypes.stream().map(CopyClassReference::uri).collect(Collectors.toSet());

        var requires = new LinkedHashMap<URI, List<URI>>();
        referenceResolver
                .dataTypeDependenciesOf(missingDataTypes)
                .forEach(
                        (uri, dependencies) -> {
                            var required = dependencies.stream().filter(listed::contains).toList();
                            if (!required.isEmpty()) {
                                requires.put(uri, required);
                            }
                        });
        return requires;
    }

    private List<CopyClassReference> missing(
            List<CopyClassReference> references,
            CopyClassReference.Kind kind,
            Graph targetGraph,
            Set<URI> pastedUris) {
        return references.stream()
                .filter(reference -> reference.kinds().contains(kind))
                .filter(reference -> !pastedUris.contains(reference.uri()))
                .filter(reference -> !CIMResourceUtils.containsClass(targetGraph, reference.uri()))
                .toList();
    }

    private List<PasteReferenceDTO> toDTOs(
            List<CopyClassReference> references,
            CopyClassReference.Kind kind,
            Map<URI, List<URI>> requires) {
        return references.stream()
                .map(
                        reference ->
                                new PasteReferenceDTO(
                                        reference.label(),
                                        reference.uri(),
                                        usedBy(reference, kind),
                                        requires.getOrDefault(reference.uri(), List.of())))
                .toList();
    }

    private List<PasteUsageDTO> usedBy(CopyClassReference reference, CopyClassReference.Kind kind) {
        return reference.usedBy().getOrDefault(kind, Set.of()).stream()
                .map(usage -> new PasteUsageDTO(usage.className(), usage.memberName()))
                .toList();
    }
}
