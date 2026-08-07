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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.PasteClassesRequestDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClassCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CopyClassService implements CopyClassUseCase {

    private final DatabasePort databasePort;
    private final CopyClassSourceReader sourceReader;
    private final CopyClassReferenceResolver referenceResolver;
    private final boolean newValuesAsBlankNode;

    public CopyClassService(
            DatabasePort databasePort,
            CopyClassSourceReader sourceReader,
            CopyClassReferenceResolver referenceResolver,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode) {
        this.databasePort = databasePort;
        this.sourceReader = sourceReader;
        this.referenceResolver = referenceResolver;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
    }

    @Override
    public List<CopyClassResponseDTO> copyClasses(
            PasteClassesRequestDTO pasteRequest, GraphIdentifier targetGraphIdentifier) {

        var sources = sourceReader.toSources(pasteRequest.getSources());
        if (sources.isEmpty()) {
            return List.of();
        }
        var options =
                toCopyOptions(
                        pasteRequest,
                        resolveTargetPackage(
                                pasteRequest.getTargetPackageUUID(), targetGraphIdentifier));

        var sourceClasses = sourceReader.readSourceClasses(sources);
        var references = resolveSelectedReferences(sources, sourceClasses, options);
        var referencedClasses = readReferencedClasses(references);

        var cimPackage = options.targetPackage();
        var prefixMapping = databasePort.getPrefixMapping(targetGraphIdentifier.datasetName());

        var responses = new ArrayList<CopyClassResponseDTO>();
        var messages = new ArrayList<String>();

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.WRITE)) {
            var targetGraph = ctx.getRdfGraph();

            for (var cimClass : sourceClasses) {
                var label = UniqueLabelFactory.uniqueClassLabel(targetGraph, cimClass.getLabel());

                var newCimClass = copyCimClass(cimClass, cimPackage, label, targetGraph, options);

                var newClassUUID =
                        CIMUpdates.insertUMLAdaptedClass(
                                targetGraph, prefixMapping, newCimClass, newValuesAsBlankNode);

                messages.add(buildCopyMessage(cimClass, options, label));
                responses.add(new CopyClassResponseDTO(newClassUUID.toString(), label.getValue()));
            }

            messages.addAll(
                    copyReferencedClasses(
                            references,
                            referencedClasses,
                            options,
                            cimPackage,
                            targetGraph,
                            prefixMapping));

            ctx.commit(buildCommitMessage(messages));
        }

        return responses;
    }

    private List<CopyClassReference> resolveSelectedReferences(
            List<CopyClassSource> sources,
            List<CIMClassUMLAdapted> sourceClasses,
            CopyClassOptions options) {

        if (options.referencesToCopy().isEmpty()) {
            return List.of();
        }
        var selected =
                referenceResolver.resolve(sources, sourceClasses).stream()
                        .filter(options::copies)
                        .toList();
        return Stream.concat(
                        selected.stream(), referenceResolver.resolveDataTypesOf(selected).stream())
                .toList();
    }

    private Map<URI, CIMClassUMLAdapted> readReferencedClasses(
            List<CopyClassReference> references) {
        if (references.isEmpty()) {
            return Map.of();
        }
        var referencedClasses = new LinkedHashMap<URI, CIMClassUMLAdapted>();
        var sources = references.stream().map(CopyClassReference::toSource).toList();
        for (var sourceClass : sourceReader.readSourceClasses(sources)) {
            if (sourceClass != null) {
                referencedClasses.put(sourceClass.getUri(), sourceClass);
            }
        }
        return referencedClasses;
    }

    private ICIMClassCategory resolveTargetPackage(
            UUID packageUUID, GraphIdentifier targetGraphIdentifier) {
        if (packageUUID == null) {
            return null;
        }
        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.READ)) {
            var targetPackage =
                    new CIMModelFacade(
                                    targetGraphIdentifier.graphUri(),
                                    ModelFactory.createModelForGraph(ctx.getRdfGraph()))
                            .getCIMClassCategory(packageUUID);
            if (targetPackage == null) {
                throw new DataAccessException("Target package not found: " + packageUUID);
            }
            return targetPackage;
        }
    }

    private CopyClassOptions toCopyOptions(
            PasteClassesRequestDTO pasteRequest, ICIMClassCategory targetPackage) {
        return new CopyClassOptions(
                targetPackage,
                pasteRequest.isCopyAsAbstract(),
                pasteRequest.isCopyAttributes(),
                pasteRequest.isCopyAssociations(),
                pasteRequest.isCopyInheritance(),
                pasteRequest.getReferencesToCopy() == null
                        ? Set.of()
                        : Set.copyOf(pasteRequest.getReferencesToCopy()));
    }

    private List<String> copyReferencedClasses(
            List<CopyClassReference> references,
            Map<URI, CIMClassUMLAdapted> referencedClasses,
            CopyClassOptions options,
            ICIMClassCategory cimPackage,
            Graph targetGraph,
            PrefixMapping prefixMapping) {

        var copiedUris =
                references.stream().map(CopyClassReference::uri).collect(Collectors.toSet());

        var messages = new ArrayList<String>();
        for (var reference : references) {
            var sourceClass = referencedClasses.get(reference.uri());
            if (sourceClass != null
                    && copyReferencedClass(
                            reference,
                            sourceClass,
                            options,
                            cimPackage,
                            targetGraph,
                            prefixMapping,
                            copiedUris)) {
                messages.add(buildReferenceMessage(reference, options));
            }
        }
        return messages;
    }

    private boolean copyReferencedClass(
            CopyClassReference reference,
            CIMClassUMLAdapted sourceClass,
            CopyClassOptions options,
            ICIMClassCategory cimPackage,
            Graph targetGraph,
            PrefixMapping prefixMapping,
            Set<URI> copiedUris) {

        if (CIMResourceUtils.containsClass(targetGraph, sourceClass.getUri())) {
            return false;
        }

        var newCimClass =
                copyClassBase(
                        sourceClass,
                        sourceClass.getUri(),
                        sourceClass.getLabel(),
                        sourceClass.getStereotypes(),
                        resolvableSuperClass(sourceClass, targetGraph, copiedUris),
                        cimPackage);
        if (options.copiesMembersOf(reference)) {
            copyMembers(sourceClass, newCimClass);
        }

        CIMUpdates.insertUMLAdaptedClass(
                targetGraph, prefixMapping, newCimClass, newValuesAsBlankNode);
        return true;
    }

    private RDFSSubClassOf resolvableSuperClass(
            CIMClassUMLAdapted sourceClass, Graph targetGraph, Set<URI> copiedUris) {
        var superClass = sourceClass.getSuperClass();
        if (superClass == null) {
            return null;
        }
        if (copiedUris.contains(superClass.getUri())
                || CIMResourceUtils.containsClass(targetGraph, superClass.getUri())) {
            return superClass;
        }
        return null;
    }

    private String buildReferenceMessage(CopyClassReference reference, CopyClassOptions options) {
        var kinds =
                reference.kinds().stream()
                        .map(CopyClassReference.Kind::getDisplayName)
                        .collect(Collectors.joining(" and "));
        return "Copied %s %s to %s"
                .formatted(kinds, reference.label(), options.targetPackageLabel());
    }

    private String buildCommitMessage(List<String> messages) {
        if (messages.size() == 1) {
            return messages.get(0);
        }
        return "Pasted %d classes:%n%s"
                .formatted(
                        messages.size(),
                        messages.stream().collect(Collectors.joining(String.format("%n"))));
    }

    private String buildCopyMessage(
            CIMClassUMLAdapted sourceClass, CopyClassOptions options, RDFSLabel label) {
        var sourcePackage =
                sourceClass.getBelongsToCategory() != null
                        ? sourceClass.getBelongsToCategory().getLabel().getValue()
                        : "default";

        var omitted = new ArrayList<String>();
        if (!options.copyAttributes()) {
            omitted.add("attributes");
        }
        if (!options.copyAssociations()) {
            omitted.add("associations");
        }
        if (!options.copyInheritance()) {
            omitted.add("inheritance");
        }

        var suffix = options.copyAsAbstract() ? " (abstract)" : "";
        if (!omitted.isEmpty()) {
            suffix += " without " + String.join(", ", omitted);
        }

        return "Copied class %s from %s to %s as %s%s"
                .formatted(
                        sourceClass.getLabel().getValue(),
                        sourcePackage,
                        options.targetPackageLabel(),
                        label.getValue(),
                        suffix);
    }

    private CIMClassUMLAdapted copyCimClass(
            CIMClassUMLAdapted cimClass,
            ICIMClassCategory cimPackage,
            RDFSLabel label,
            Graph targetGraph,
            CopyClassOptions options) {
        var newCimClass =
                copyClassBase(
                        cimClass,
                        new URI(cimClass.getUri().getPrefix() + label.getValue()),
                        label,
                        options.copyAsAbstract()
                                ? CIMStereotypes.withoutConcrete(cimClass.getStereotypes())
                                : cimClass.getStereotypes(),
                        options.copyInheritance() ? cimClass.getSuperClass() : null,
                        cimPackage);
        if (options.copyAttributes()) {
            copyMembers(cimClass, newCimClass);
        }
        if (options.copyAssociations()) {
            newCimClass.setAssociationPairs(copyAssociations(cimClass, newCimClass, targetGraph));
        }
        return newCimClass;
    }

    private CIMClassUMLAdapted copyClassBase(
            CIMClassUMLAdapted sourceClass,
            URI uri,
            RDFSLabel label,
            List<CIMSStereotype> stereotypes,
            RDFSSubClassOf superClass,
            ICIMClassCategory cimPackage) {
        var newCimClass =
                CIMClassUMLAdapted.builder()
                        .uuid(UUID.randomUUID())
                        .uri(uri)
                        .label(label)
                        .stereotypes(stereotypes)
                        .superClass(superClass)
                        .attributes(List.of())
                        .enumEntries(List.of())
                        .associationPairs(List.of())
                        .build();
        if (sourceClass.getComment() != null) {
            newCimClass.setComment(
                    new RDFSComment(
                            sourceClass.getComment().getValue(),
                            sourceClass.getComment().getFormat()));
        }
        if (cimPackage != null) {
            newCimClass.setBelongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }
        return newCimClass;
    }

    private void copyMembers(CIMClassUMLAdapted sourceClass, CIMClassUMLAdapted newCimClass) {
        newCimClass.setAttributes(copyAttributes(sourceClass.getAttributes(), newCimClass));
        newCimClass.setEnumEntries(copyEnumEntries(sourceClass.getEnumEntries(), newCimClass));
    }

    private List<CIMAttribute> copyAttributes(List<CIMAttribute> attributes, CIMClass cimClass) {
        return attributes.stream()
                .map(
                        attr -> {
                            var uri =
                                    new URI(
                                            cimClass.getUri().getPrefix()
                                                    + cimClass.getUri().getSuffix()
                                                    + "."
                                                    + attr.getLabel().getValue());
                            var domain =
                                    new RDFSDomain(
                                            cimClass.getUri(),
                                            new RDFSLabel(cimClass.getUri().getSuffix(), "en"));
                            return attr.toBuilder()
                                    .uuid(UUID.randomUUID())
                                    .uri(uri)
                                    .domain(domain)
                                    .build();
                        })
                .toList();
    }

    private List<CIMEnumEntry> copyEnumEntries(List<CIMEnumEntry> enumEntries, CIMClass cimClass) {
        return enumEntries.stream()
                .map(
                        entry -> {
                            var rdfType =
                                    new RDFType(
                                            cimClass.getUri(),
                                            new RDFSLabel(cimClass.getUri().getSuffix(), "en"));
                            var uri =
                                    new URI(
                                            cimClass.getUri().getPrefix()
                                                    + cimClass.getUri().getSuffix()
                                                    + "."
                                                    + entry.getLabel().getValue());
                            return entry.toBuilder()
                                    .uuid(UUID.randomUUID())
                                    .type(rdfType)
                                    .uri(uri)
                                    .build();
                        })
                .toList();
    }

    private List<CIMAssociationPair> copyAssociations(
            CIMClassUMLAdapted sourceClass, CIMClass newClass, Graph graph) {
        return ClassAssociations.ownedBy(sourceClass).stream()
                .map(pair -> copyAssociationPair(pair, newClass, graph))
                .filter(Objects::nonNull)
                .toList();
    }

    private CIMAssociationPair copyAssociationPair(
            CIMAssociationPair pair, CIMClass newClass, Graph graph) {
        var existingToLabels =
                UniqueLabelFactory.existingAssociationLabels(
                        graph, pair.getTo().getDomain().getUri(), pair.getTo().getLabel());
        var newToLabel = UniqueLabelFactory.uniqueLabel(pair.getTo().getLabel(), existingToLabels);
        var existingFromLabels =
                UniqueLabelFactory.existingAssociationLabels(
                        graph, newClass.getUri(), pair.getFrom().getLabel());
        if (!existingFromLabels.isEmpty()) {
            return null;
        }
        var newFromLabel = pair.getFrom().getLabel();

        var from = buildFromAssociation(pair.getFrom(), newClass, newToLabel, newFromLabel);
        var to = buildToAssociation(pair.getTo(), newClass, newToLabel, newFromLabel);

        return new CIMAssociationPair(from, to);
    }

    private CIMAssociation buildFromAssociation(
            CIMAssociation original,
            CIMClass newClass,
            RDFSLabel newToLabel,
            RDFSLabel newFromLabel) {
        return original.toBuilder()
                .uuid(UUID.randomUUID())
                .label(newFromLabel)
                .uri(new URI(newClass.getUri() + "." + newFromLabel.getValue()))
                .domain(
                        new RDFSDomain(
                                newClass.getUri(),
                                new RDFSLabel(newClass.getUri().getSuffix(), "en")))
                .inverseRoleName(
                        new CIMSInverseRoleName(
                                original.getRange().getUri() + "." + newToLabel.getValue()))
                .build();
    }

    private CIMAssociation buildToAssociation(
            CIMAssociation original,
            CIMClass newClass,
            RDFSLabel newToLabel,
            RDFSLabel newFromLabel) {
        return original.toBuilder()
                .uuid(UUID.randomUUID())
                .label(newToLabel)
                .uri(new URI(original.getDomain().getUri() + "." + newToLabel.getValue()))
                .range(
                        new RDFSRange(
                                newClass.getUri(),
                                new RDFSLabel(newClass.getUri().getSuffix(), "en")))
                .inverseRoleName(
                        new CIMSInverseRoleName(newClass.getUri() + "." + newFromLabel.getValue()))
                .build();
    }
}
