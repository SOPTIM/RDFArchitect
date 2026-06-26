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

import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.PasteClassesRequestDTO;
import org.rdfarchitect.api.dto.PasteSourceClassDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CopyClassService implements CopyClassUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;
    private final ExpandURIUseCase expandURIUseCase;
    private final boolean newValuesAsBlankNode;

    public CopyClassService(
            DatabasePort databasePort,
            PackageMapper packageMapper,
            ExpandURIUseCase expandURIUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode) {
        this.databasePort = databasePort;
        this.packageMapper = packageMapper;
        this.expandURIUseCase = expandURIUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
    }

    @Override
    public List<CopyClassResponseDTO> copyClasses(
            PasteClassesRequestDTO pasteRequest, GraphIdentifier targetGraphIdentifier) {

        var sources = toSources(pasteRequest.getSources());
        if (sources.isEmpty()) {
            return List.of();
        }
        var options = toCopyOptions(pasteRequest);

        var sourceClasses =
                sources.stream()
                        .map(
                                source ->
                                        readSourceClass(
                                                source.graphIdentifier(),
                                                source.classUUID().toString()))
                        .toList();

        var cimPackage = packageMapper.toCIMObject(options.targetPackage());
        var prefixMapping = databasePort.getPrefixMapping(targetGraphIdentifier.datasetName());

        var responses = new ArrayList<CopyClassResponseDTO>();
        var messages = new ArrayList<String>();

        try (var ctx =
                databasePort.getGraphWithContext(targetGraphIdentifier).begin(ReadWrite.WRITE)) {
            var targetGraph = ctx.getRdfGraph();

            for (var cimClass : sourceClasses) {
                var label =
                        constructUniqueLabel(
                                cimClass.getLabel(),
                                getExistingClassLabels(targetGraph, cimClass.getLabel()));

                var newCimClass =
                        copyCimClass(
                                cimClass,
                                cimPackage,
                                label,
                                targetGraph,
                                options.copyAsAbstract(),
                                options.copyAttributes(),
                                options.copyAssociations());

                var newClassUUID =
                        CIMUpdates.insertUMLAdaptedClass(
                                targetGraph, prefixMapping, newCimClass, newValuesAsBlankNode);

                messages.add(buildCopyMessage(cimClass, options, label));
                responses.add(new CopyClassResponseDTO(newClassUUID.toString(), label.getValue()));
            }

            ctx.commit(buildCommitMessage(messages));
        }

        return responses;
    }

    private List<CopyClassSource> toSources(List<PasteSourceClassDTO> sourceDTOs) {
        if (sourceDTOs == null) {
            return List.of();
        }
        return sourceDTOs.stream()
                .map(
                        dto -> {
                            var expandedGraphURI =
                                    expandURIUseCase.expandUri(
                                            dto.getSourceDatasetName(), dto.getSourceGraphURI());
                            var sourceGraphIdentifier =
                                    new GraphIdentifier(
                                            dto.getSourceDatasetName(), expandedGraphURI);
                            return new CopyClassSource(
                                    sourceGraphIdentifier, UUID.fromString(dto.getClassUUID()));
                        })
                .toList();
    }

    private CopyClassOptions toCopyOptions(PasteClassesRequestDTO pasteRequest) {
        return new CopyClassOptions(
                pasteRequest.getTargetPackage(),
                pasteRequest.isCopyAsAbstract(),
                pasteRequest.isCopyAttributes(),
                pasteRequest.isCopyAssociations());
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

    private CIMClassUMLAdapted readSourceClass(GraphIdentifier graphIdentifier, String classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return CIMUMLObjectFactory.createCIMClassUMLAdapted(
                    ctx.getRdfGraph(),
                    graphIdentifier.graphUri(),
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    classUUID);
        }
    }

    private String buildCopyMessage(
            CIMClassUMLAdapted sourceClass, CopyClassOptions options, RDFSLabel label) {
        var sourcePackage =
                sourceClass.getBelongsToCategory() != null
                        ? sourceClass.getBelongsToCategory().getLabel().getValue()
                        : "default";
        var targetPackage =
                options.targetPackage() != null ? options.targetPackage().getLabel() : "default";

        var suffix = "";
        if (options.copyAsAbstract()) {
            suffix = " bare";
        } else if (!options.copyAttributes()) {
            suffix = " without attributes";
        } else if (!options.copyAssociations()) {
            suffix = " without associations";
        }

        return "Copied class %s from %s to %s as %s%s"
                .formatted(
                        sourceClass.getLabel().getValue(),
                        sourcePackage,
                        targetPackage,
                        label.getValue(),
                        suffix);
    }

    private CIMClassUMLAdapted copyCimClass(
            CIMClassUMLAdapted cimClass,
            CIMPackage cimPackage,
            RDFSLabel label,
            Graph targetGraph,
            boolean copyAsAbstract,
            boolean copyAttributes,
            boolean copyAssociations) {
        var stereotypes =
                copyAsAbstract
                        ? cimClass.getStereotypes().stream()
                                .filter(
                                        s ->
                                                !s.getStereotype()
                                                        .equals(CIMStereotypes.concreteString))
                                .toList()
                        : cimClass.getStereotypes();
        var newCimClass =
                CIMClassUMLAdapted.builder()
                        .uuid(UUID.randomUUID())
                        .uri(new URI(cimClass.getUri().getPrefix() + label.getValue()))
                        .label(label)
                        .stereotypes(stereotypes);
        if (!copyAsAbstract) {
            newCimClass.superClass(cimClass.getSuperClass());
        }
        if (cimClass.getComment() != null) {
            newCimClass.comment(
                    new RDFSComment(
                            cimClass.getComment().getValue(), cimClass.getComment().getFormat()));
        }
        if (cimPackage != null) {
            newCimClass.belongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }
        newCimClass.attributes(
                copyAttributes(cimClass.getAttributes(), newCimClass.build(), copyAttributes));
        newCimClass.enumEntries(
                copyEnumEntries(cimClass.getEnumEntries(), newCimClass.build(), copyAttributes));
        newCimClass.associationPairs(
                copyAssociations(
                        cimClass.getAssociationPairs(),
                        cimClass,
                        newCimClass.build(),
                        targetGraph,
                        copyAssociations));

        return newCimClass.build();
    }

    private List<CIMAttribute> copyAttributes(
            List<CIMAttribute> attributes, CIMClass cimClass, boolean copyAttributes) {
        if (!copyAttributes) {
            return List.of();
        }
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

    private List<CIMEnumEntry> copyEnumEntries(
            List<CIMEnumEntry> enumEntries, CIMClass cimClass, boolean copyEnumEntries) {
        if (!copyEnumEntries) {
            return List.of();
        }
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
            List<CIMAssociationPair> pairs,
            CIMClass sourceClass,
            CIMClass newClass,
            Graph graph,
            boolean copyAssociations) {
        if (!copyAssociations) {
            return List.of();
        }
        return pairs.stream()
                .filter(pair -> pair.getFrom().getDomain().getUri().equals(sourceClass.getUri()))
                .map(pair -> copyAssociationPair(pair, newClass, graph))
                .filter(Objects::nonNull)
                .toList();
    }

    private CIMAssociationPair copyAssociationPair(
            CIMAssociationPair pair, CIMClass newClass, Graph graph) {
        var existingToLabels =
                getExistingAssociationLabels(
                        graph, pair.getTo().getDomain().getUri(), pair.getTo().getLabel());
        var newToLabel = constructUniqueLabel(pair.getTo().getLabel(), existingToLabels);
        var existingFromLabels =
                getExistingAssociationLabels(graph, newClass.getUri(), pair.getFrom().getLabel());
        if (!existingFromLabels.isEmpty()) {
            return null;
        }
        var newFromLabel = constructUniqueLabel(pair.getFrom().getLabel(), existingFromLabels);

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

    private Set<String> getExistingClassLabels(Graph graph, RDFSLabel label) {
        var baseValue = label.getValue();
        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addWhere("?s", RDF.type, RDFS.Class)
                        .addWhere("?s", RDFS.label, "?label")
                        .addFilter(exprFactory.strstarts(exprFactory.str("?label"), baseValue))
                        .build();

        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        try (var queryExecution = QueryExecutionFactory.create(query, dataset)) {
            var resultSet = ResultSetFactory.copyResults(queryExecution.execSelect());
            var existingLabels = new HashSet<String>();
            while (resultSet.hasNext()) {
                existingLabels.add(resultSet.nextSolution().getLiteral("label").getString());
            }
            return existingLabels;
        }
    }

    private Set<String> getExistingAssociationLabels(Graph graph, URI domainUri, RDFSLabel label) {
        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addWhere("?s", RDFS.domain, NodeFactory.createURI(domainUri.toString()))
                        .addWhere("?s", RDFS.label, "?label")
                        .addFilter(
                                exprFactory.strstarts(exprFactory.str("?label"), label.getValue()))
                        .build();

        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        try (var queryExecution = QueryExecutionFactory.create(query, dataset)) {
            var resultSet = ResultSetFactory.copyResults(queryExecution.execSelect());
            var existingLabels = new HashSet<String>();
            while (resultSet.hasNext()) {
                existingLabels.add(resultSet.nextSolution().getLiteral("label").getString());
            }
            return existingLabels;
        }
    }

    private RDFSLabel constructUniqueLabel(RDFSLabel label, Set<String> existingLabels) {
        var baseValue = label.getValue();
        if (!existingLabels.contains(baseValue)) {
            return new RDFSLabel(baseValue, label.getLang());
        }
        baseValue = label.getValue() + "-Copy";
        if (!existingLabels.contains(baseValue)) {
            return new RDFSLabel(baseValue, label.getLang());
        }
        var counter = 1;
        while (existingLabels.contains(baseValue + "(" + counter + ")")) {
            counter++;
        }
        return new RDFSLabel(baseValue + "(" + counter + ")", label.getLang());
    }
}
