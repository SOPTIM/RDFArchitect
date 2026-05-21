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
import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.CopyClassRequestDTO;
import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.dl.ClassLayoutPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutor;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
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
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.services.diagrams.RemoveFromDiagramUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.DeleteClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateDiagramObjectNameUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateClassService
        implements AddClassUseCase, ReplaceClassUseCase, DeleteClassUseCase, CopyClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;
    private final boolean newValuesAsBlankNode;

    private final CreateClassLayoutDataUseCase createClassLayoutDataUseCase;
    private final UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase;
    private final DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase;
    private final RemoveFromDiagramUseCase removeFromDiagramUseCase;

    public UpdateClassService(
            DatabasePort databasePort,
            ClassUMLAdaptedMapper classMapper,
            PackageMapper packageMapper,
            CreateClassLayoutDataUseCase createClassLayoutDataUseCase,
            UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase,
            DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode,
            RemoveFromDiagramUseCase removeFromDiagramUseCase) {
        this.databasePort = databasePort;
        this.classMapper = classMapper;
        this.packageMapper = packageMapper;
        this.createClassLayoutDataUseCase = createClassLayoutDataUseCase;
        this.updateDiagramObjectNameUseCase = updateDiagramObjectNameUseCase;
        this.deleteClassLayoutDataUseCase = deleteClassLayoutDataUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
        this.removeFromDiagramUseCase = removeFromDiagramUseCase;
    }

    @Override
    public void replaceClass(GraphIdentifier graphIdentifier, ClassUMLAdaptedDTO newClass) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var cimClass = classMapper.toCIMObject(newClass);
            assertNoPackageWithSameIri(graph, cimClass);
            CIMUpdates.replaceClass(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    cimClass,
                    newValuesAsBlankNode);
            ctx.commit(
                    "Updated class \"%s\" (%s)".formatted(newClass.getLabel(), newClass.getUuid()));
        }

        updateDiagramObjectNameUseCase.updateDiagramObjectName(
                graphIdentifier, newClass.getUuid(), newClass.getLabel());
    }

    @Override
    public UUID addClass(
            GraphIdentifier graphIdentifier,
            PackageDTO packageDTO,
            String classURIPrefix,
            String className,
            ClassLayoutPositionDTO classLayoutPosition) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        UUID newClassUUID;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            var newClass = constructClass(cimPackage, classURIPrefix, className);
            assertNoPackageWithSameIri(graph, newClass);
            newClassUUID =
                    CIMUpdates.insertClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            newClass);
            ctx.commit("Added class \"%s\" (%s)".formatted(newClass.getLabel(), newClassUUID));
        }

        createClassLayoutDataUseCase.createClassLayoutData(
                graphIdentifier, packageDTO, className, newClassUUID, classLayoutPosition);

        return newClassUUID;
    }

    private CIMClass constructClass(
            CIMPackage cimPackage, String classURIPrefix, String classLabel) {
        var cimClass =
                CIMClass.builder()
                        .uri(new URI(classURIPrefix + classLabel))
                        .label(new RDFSLabel(classLabel, "en"))
                        .superClass(null)
                        .comment(null);
        if (cimPackage != null) {
            cimClass.belongsToCategory(
                    new CIMSBelongsToCategory(
                            cimPackage.getUri(), cimPackage.getLabel(), cimPackage.getUuid()));
        }
        return cimClass.build();
    }

    private void assertNoPackageWithSameIri(Graph graph, CIMClass newClass) {
        var classUri = newClass.getUri().toNode();
        if (graph.contains(classUri, RDF.type.asNode(), CIMS.classCategory.asNode())) {
            throw new ResourceConflictException(
                    "Cannot save class "
                            + newClass.getUri()
                            + " because a package with the same IRI already exists.");
        }
    }

    @Override
    public void deleteClass(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var classResource = CIMResourceUtils.findResourceForUuid(ctx.getRdfGraph(), classUUID);
            var classLabel = CIMResourceUtils.findLabelForResource(classResource);
            CIMUpdates.deleteClass(
                    ctx.getRdfGraph(),
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    classUUID);
            ctx.commit("Deleted class \"%s\" (%s)".formatted(classLabel, classUUID));
        }

        deleteClassLayoutDataUseCase.deleteClassLayoutData(graphIdentifier, classUUID);
        removeFromDiagramUseCase.removeFromAllDiagrams(graphIdentifier, classUUID);
    }

    @Override
    public CopyClassResponseDTO copyClass(
            GraphIdentifier graphIdentifier,
            UUID classUUID,
            GraphIdentifier targetGraphIdentifier,
            CopyClassRequestDTO copyClassRequestDTO) {

        var cimClass = readSourceClass(graphIdentifier, classUUID.toString());

        var label =
                constructUniqueLabel(
                        cimClass.getLabel(),
                        getExistingClassLabels(
                                databasePort
                                        .getGraphWithContext(targetGraphIdentifier)
                                        .getRdfGraph(),
                                cimClass.getLabel()));

        var cimPackage = packageMapper.toCIMObject(copyClassRequestDTO.getTargetPackage());
        var newCimClass =
                copyCimClass(
                        targetGraphIdentifier,
                        cimClass,
                        cimPackage,
                        label,
                        copyClassRequestDTO.isCopyAsAbstract(),
                        copyClassRequestDTO.isCopyAttributes(),
                        copyClassRequestDTO.isCopyAssociations());

        var newClassUUID = insertClass(targetGraphIdentifier, newCimClass);

        changeLogUseCase.recordChange(
                targetGraphIdentifier,
                new ChangeLogEntry(
                        "Copied class "
                                + cimClass.getLabel().getValue()
                                + " from "
                                + (cimClass.getBelongsToCategory() != null
                                        ? cimClass.getBelongsToCategory().getLabel().getValue()
                                        : "default")
                                + " to "
                                + (copyClassRequestDTO.getTargetPackage() != null
                                        ? copyClassRequestDTO.getTargetPackage().getLabel()
                                        : "default")
                                + " as "
                                + label.getValue()
                                + (copyClassRequestDTO.isCopyAsAbstract()
                                        ? " bare"
                                        : !copyClassRequestDTO.isCopyAttributes()
                                                ? " without attributes"
                                                : !copyClassRequestDTO.isCopyAssociations()
                                                        ? " without associations"
                                                        : ""),
                        databasePort
                                .getGraphWithContext(targetGraphIdentifier)
                                .getRdfGraph()
                                .getLastDelta()));
        return new CopyClassResponseDTO(newClassUUID.toString(), label.getValue());
    }

    private CIMClassUMLAdapted readSourceClass(GraphIdentifier graphIdentifier, String classUUID) {
        GraphRewindableWithUUIDs graph = null;
        CIMClassUMLAdapted cimClass;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            cimClass =
                    CIMUMLObjectFactory.createCIMClassUMLAdapted(
                            graph,
                            graphIdentifier.graphUri(),
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            classUUID);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
        return cimClass;
    }

    private UUID insertClass(
            GraphIdentifier targetGraphIdentifier, CIMClassUMLAdapted newCimClass) {
        GraphRewindableWithUUIDs targetGraph = null;
        UUID newClassUUID;
        try {
            targetGraph = databasePort.getGraphWithContext(targetGraphIdentifier).getRdfGraph();
            targetGraph.begin(TxnType.WRITE);
            newClassUUID =
                    CIMUpdates.insertUMLAdaptedClass(
                            targetGraph,
                            databasePort.getPrefixMapping(targetGraphIdentifier.datasetName()),
                            newCimClass,
                            newValuesAsBlankNode);
            targetGraph.commit();
        } finally {
            if (targetGraph != null) {
                targetGraph.end();
            }
        }
        return newClassUUID;
    }

    private CIMClassUMLAdapted copyCimClass(
            GraphIdentifier targetGraphIdentifier,
            CIMClassUMLAdapted cimClass,
            CIMPackage cimPackage,
            RDFSLabel label,
            boolean copyAsAbstract,
            boolean copyAttributes,
            boolean copyAssociations) {
        var targetGraph = databasePort.getGraphWithContext(targetGraphIdentifier).getRdfGraph();
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
            GraphRewindableWithUUIDs graph,
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
            CIMAssociationPair pair, CIMClass newClass, GraphRewindableWithUUIDs graph) {
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

    private Set<String> getExistingClassLabels(GraphRewindableWithUUIDs graph, RDFSLabel label) {
        var baseValue = label.getValue();

        final var LABEL_VAR = "?label";
        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar(LABEL_VAR)
                        .addWhere("?s", RDF.type, RDFS.Class)
                        .addWhere("?s", RDFS.label, LABEL_VAR)
                        .addFilter(exprFactory.strstarts(exprFactory.str(LABEL_VAR), baseValue))
                        .build();
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graph, query, null);

        var existingLabels = new HashSet<String>();
        while (resultSet.hasNext()) {
            var solution = resultSet.nextSolution();
            existingLabels.add(solution.getLiteral("label").getString());
        }
        return existingLabels;
    }

    private Set<String> getExistingAssociationLabels(
            GraphRewindableWithUUIDs graph, URI domainUri, RDFSLabel label) {
        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addWhere("?s", RDFS.domain, NodeFactory.createURI(domainUri.toString()))
                        .addWhere("?s", RDFS.label, "?label")
                        .addFilter(
                                exprFactory.strstarts(exprFactory.str("?label"), label.getValue()))
                        .build();
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graph, query, null);
        var existingLabels = new HashSet<String>();
        while (resultSet.hasNext()) {
            existingLabels.add(resultSet.nextSolution().getLiteral("label").getString());
        }
        return existingLabels;
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
