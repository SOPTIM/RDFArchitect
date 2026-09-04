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

import lombok.extern.slf4j.Slf4j;

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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAssociation;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClassCategory;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader.ResolvedSource;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader.SourceSnapshots;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
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

        var snapshots = sourceReader.newSourceSnapshots();
        var resolvedSources = sourceReader.readSources(sources, snapshots);
        if (resolvedSources.size() != sources.size()) {
            log.warn(
                    "Pasting nothing because only {} of the {} copied classes are still available.",
                    resolvedSources.size(),
                    sources.size());
            return List.of();
        }
        var references = resolveSelectedReferences(resolvedSources, options, targetGraphIdentifier);
        var referencedClasses = readReferencedClasses(references, snapshots);

        var cimPackage = options.targetPackage();
        var prefixMapping = databasePort.getPrefixMapping(targetGraphIdentifier.datasetName());

        var responses = new ArrayList<CopyClassResponseDTO>();
        var messages = new ArrayList<String>();

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.WRITE)) {
            var targetGraph = ctx.getRdfGraph();
            var batch = planBatch(resolvedSources, targetGraph);

            for (var pastedClass : batch.pastedClasses()) {
                var newCimClass = copyCimClass(pastedClass, cimPackage, batch, options);

                var newClassUUID =
                        CIMUpdates.insertUMLAdaptedClass(
                                targetGraph, prefixMapping, newCimClass, newValuesAsBlankNode);

                messages.add(
                        buildCopyMessage(pastedClass.sourceClass(), options, pastedClass.label()));
                responses.add(
                        new CopyClassResponseDTO(
                                newClassUUID.toString(), pastedClass.label().getValue()));
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

    private PasteBatch planBatch(List<ResolvedSource> resolvedSources, Graph targetGraph) {
        var takenLabels = new HashSet<String>();
        var pastedClasses = new ArrayList<PastedClass>();
        for (var resolvedSource : resolvedSources) {
            var sourceClass = resolvedSource.cimClass();
            var label =
                    UniqueLabelFactory.uniqueClassLabel(
                            targetGraph, sourceClass.getLabel(), takenLabels);
            takenLabels.add(label.getValue());
            pastedClasses.add(
                    new PastedClass(
                            sourceClass,
                            label,
                            new URI(sourceClass.getUri().getPrefix() + label.getValue())));
        }
        var pastedBySourceUri = new LinkedHashMap<URI, PastedClass>();
        pastedClasses.forEach(
                pastedClass ->
                        pastedBySourceUri.putIfAbsent(
                                pastedClass.sourceClass().getUri(), pastedClass));
        return new PasteBatch(targetGraph, pastedClasses, pastedBySourceUri);
    }

    private List<CopyClassReference> resolveSelectedReferences(
            List<ResolvedSource> sources,
            CopyClassOptions options,
            GraphIdentifier targetGraphIdentifier) {

        if (options.referencesToCopy().isEmpty()) {
            return List.of();
        }
        var selected =
                referenceResolver.resolve(sources, targetGraphIdentifier).stream()
                        .filter(options::copies)
                        .toList();
        return Stream.concat(
                        selected.stream(), referenceResolver.resolveDataTypesOf(selected).stream())
                .toList();
    }

    private Map<URI, ICIMClass> readReferencedClasses(
            List<CopyClassReference> references, SourceSnapshots snapshots) {
        if (references.isEmpty()) {
            return Map.of();
        }
        var referencedClasses = new LinkedHashMap<URI, ICIMClass>();
        var sources = references.stream().map(CopyClassReference::toSource).toList();
        for (var resolvedSource : sourceReader.readSources(sources, snapshots)) {
            referencedClasses.put(resolvedSource.cimClass().getUri(), resolvedSource.cimClass());
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
            Map<URI, ICIMClass> referencedClasses,
            CopyClassOptions options,
            ICIMClassCategory cimPackage,
            Graph targetGraph,
            PrefixMapping prefixMapping) {

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
                            prefixMapping)) {
                messages.add(buildReferenceMessage(reference, options));
            }
        }
        return messages;
    }

    private boolean copyReferencedClass(
            CopyClassReference reference,
            ICIMClass sourceClass,
            CopyClassOptions options,
            ICIMClassCategory cimPackage,
            Graph targetGraph,
            PrefixMapping prefixMapping) {

        if (CIMResourceUtils.containsClass(targetGraph, sourceClass.getUri())) {
            return false;
        }

        var newCimClass =
                copyClassBase(
                        sourceClass,
                        sourceClass.getUri(),
                        sourceClass.getLabel(),
                        sourceClass.getStereotypes(),
                        options.copyInheritance() ? superClassOf(sourceClass) : null,
                        cimPackage);
        if (options.copiesMembersOf(reference)) {
            copyMembers(sourceClass, newCimClass);
        }

        CIMUpdates.insertUMLAdaptedClass(
                targetGraph, prefixMapping, newCimClass, newValuesAsBlankNode);
        return true;
    }

    private RDFSSubClassOf superClassOf(ICIMClass sourceClass) {
        var superClasses = sourceClass.getSuperClasses();
        if (superClasses.isEmpty()) {
            return null;
        }
        var superClass = superClasses.getFirst();
        return new RDFSSubClassOf(superClass.getUri(), superClass.getLabel());
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
            ICIMClass sourceClass, CopyClassOptions options, RDFSLabel label) {
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
            PastedClass pastedClass,
            ICIMClassCategory cimPackage,
            PasteBatch batch,
            CopyClassOptions options) {
        var cimClass = pastedClass.sourceClass();
        var newCimClass =
                copyClassBase(
                        cimClass,
                        pastedClass.uri(),
                        pastedClass.label(),
                        options.copyAsAbstract()
                                ? CIMStereotypes.withoutConcrete(cimClass.getStereotypes())
                                : cimClass.getStereotypes(),
                        options.copyInheritance() ? superClassIn(cimClass, batch) : null,
                        cimPackage);
        if (options.copyAttributes()) {
            copyMembers(cimClass, newCimClass);
        }
        if (options.copyAssociations()) {
            newCimClass.setAssociationPairs(copyAssociations(cimClass, newCimClass, batch));
        }
        return newCimClass;
    }

    private RDFSSubClassOf superClassIn(ICIMClass sourceClass, PasteBatch batch) {
        var superClass = superClassOf(sourceClass);
        if (superClass == null) {
            return null;
        }
        return new RDFSSubClassOf(
                batch.uriOf(superClass.getUri()),
                batch.labelOf(superClass.getUri(), superClass.getLabel()));
    }

    private CIMClassUMLAdapted copyClassBase(
            ICIMClass sourceClass,
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
        newCimClass.setComment(sourceClass.getComment());
        if (cimPackage != null) {
            newCimClass.setBelongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }
        return newCimClass;
    }

    private void copyMembers(ICIMClass sourceClass, CIMClassUMLAdapted newCimClass) {
        newCimClass.setAttributes(copyAttributes(sourceClass.getAttributes(), newCimClass));
        newCimClass.setEnumEntries(copyEnumEntries(sourceClass.getEnumEntries(), newCimClass));
    }

    private List<CIMAttribute> copyAttributes(List<ICIMAttribute> attributes, CIMClass cimClass) {
        return attributes.stream()
                .map(
                        attr ->
                                CIMAttribute.builder()
                                        .uuid(UUID.randomUUID())
                                        .uri(memberUri(cimClass, attr.getLabel()))
                                        .label(attr.getLabel())
                                        .domain(ownDomain(cimClass))
                                        .multiplicity(attr.getMultiplicity())
                                        .dataType(copyDataType(attr))
                                        .comment(attr.getComment())
                                        .stereotype(attr.getStereotype())
                                        .fixedValue(attr.getFixed())
                                        .defaultValue(attr.getDefault())
                                        .build())
                .toList();
    }

    private CIMSDataType copyDataType(ICIMAttribute attribute) {
        var kind = attribute.getDataTypeKind();
        if (kind == CIMSDataType.Type.UNKNOWN) {
            return null;
        }
        var dataType = attribute.getDataType();
        if (kind == CIMSDataType.Type.PRIMITIVE) {
            return new CIMSPrimitiveDataType(dataType.getUri(), dataType.getLabel());
        }
        return new RDFSRange(dataType.getUri(), dataType.getLabel());
    }

    private List<CIMEnumEntry> copyEnumEntries(List<ICIMEnumEntry> enumEntries, CIMClass cimClass) {
        return enumEntries.stream()
                .map(
                        entry ->
                                CIMEnumEntry.builder()
                                        .uuid(UUID.randomUUID())
                                        .uri(memberUri(cimClass, entry.getLabel()))
                                        .type(new RDFType(cimClass.getUri(), ownLabel(cimClass)))
                                        .label(entry.getLabel())
                                        .comment(entry.getComment())
                                        .stereotype(entry.getStereotype())
                                        .build())
                .toList();
    }

    private URI memberUri(CIMClass cimClass, RDFSLabel memberLabel) {
        return new URI(
                cimClass.getUri().getPrefix()
                        + cimClass.getUri().getSuffix()
                        + "."
                        + memberLabel.getValue());
    }

    private RDFSDomain ownDomain(CIMClass cimClass) {
        return new RDFSDomain(cimClass.getUri(), ownLabel(cimClass));
    }

    private RDFSLabel ownLabel(CIMClass cimClass) {
        return new RDFSLabel(cimClass.getUri().getSuffix(), "en");
    }

    private List<CIMAssociationPair> copyAssociations(
            ICIMClass sourceClass, CIMClass newClass, PasteBatch batch) {
        return sourceClass.getAssociations().stream()
                .map(association -> copyAssociationPair(association, newClass, batch))
                .filter(Objects::nonNull)
                .toList();
    }

    private CIMAssociationPair copyAssociationPair(
            ICIMAssociation from, CIMClass newClass, PasteBatch batch) {
        try {
            return buildAssociationPair(from, newClass, batch);
        } catch (IllegalStateException exception) {
            log.warn(
                    "Skipping an association of class '{}' because it is incomplete: {}",
                    newClass.getUri(),
                    exception.getMessage());
            return null;
        }
    }

    private CIMAssociationPair buildAssociationPair(
            ICIMAssociation from, CIMClass newClass, PasteBatch batch) {
        var to = from.getInverseAssociation();
        var existingToLabels =
                UniqueLabelFactory.existingAssociationLabels(
                        batch.targetGraph(), batch.uriOf(to.getDomain().getUri()), to.getLabel());
        var newToLabel = UniqueLabelFactory.uniqueLabel(to.getLabel(), existingToLabels);
        var existingFromLabels =
                UniqueLabelFactory.existingAssociationLabels(
                        batch.targetGraph(), newClass.getUri(), from.getLabel());
        if (!existingFromLabels.isEmpty()) {
            return null;
        }
        var newFromLabel = from.getLabel();

        return new CIMAssociationPair(
                buildFromAssociation(from, newClass, batch, newToLabel, newFromLabel),
                buildToAssociation(to, newClass, batch, newToLabel, newFromLabel));
    }

    private CIMAssociation buildFromAssociation(
            ICIMAssociation original,
            CIMClass newClass,
            PasteBatch batch,
            RDFSLabel newToLabel,
            RDFSLabel newFromLabel) {
        var range = original.getRange();
        var rangeUri = batch.uriOf(range.getUri());
        return CIMAssociation.builder()
                .uuid(UUID.randomUUID())
                .label(newFromLabel)
                .uri(new URI(newClass.getUri() + "." + newFromLabel.getValue()))
                .comment(original.getComment())
                .multiplicity(original.getMultiplicity())
                .domain(ownDomain(newClass))
                .range(new RDFSRange(rangeUri, batch.labelOf(range.getUri(), range.getLabel())))
                .associationUsed(original.getAssociationUsed())
                .inverseRoleName(new CIMSInverseRoleName(rangeUri + "." + newToLabel.getValue()))
                .build();
    }

    private CIMAssociation buildToAssociation(
            ICIMAssociation original,
            CIMClass newClass,
            PasteBatch batch,
            RDFSLabel newToLabel,
            RDFSLabel newFromLabel) {
        var domain = original.getDomain();
        var domainUri = batch.uriOf(domain.getUri());
        return CIMAssociation.builder()
                .uuid(UUID.randomUUID())
                .label(newToLabel)
                .uri(new URI(domainUri + "." + newToLabel.getValue()))
                .comment(original.getComment())
                .multiplicity(original.getMultiplicity())
                .domain(
                        new RDFSDomain(
                                domainUri, batch.labelOf(domain.getUri(), domain.getLabel())))
                .range(new RDFSRange(newClass.getUri(), ownLabel(newClass)))
                .associationUsed(original.getAssociationUsed())
                .inverseRoleName(
                        new CIMSInverseRoleName(newClass.getUri() + "." + newFromLabel.getValue()))
                .build();
    }

    private record PastedClass(ICIMClass sourceClass, RDFSLabel label, URI uri) {}

    private record PasteBatch(
            Graph targetGraph,
            List<PastedClass> pastedClasses,
            Map<URI, PastedClass> pastedBySourceUri) {

        private URI uriOf(URI sourceUri) {
            var pastedClass = pastedBySourceUri.get(sourceUri);
            return pastedClass == null ? sourceUri : pastedClass.uri();
        }

        private RDFSLabel labelOf(URI sourceUri, RDFSLabel sourceLabel) {
            var pastedClass = pastedBySourceUri.get(sourceUri);
            return pastedClass == null ? sourceLabel : pastedClass.label();
        }
    }
}
