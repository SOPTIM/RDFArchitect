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
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutor;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
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
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.classlayout.CreateClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.DeleteClassLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateDiagramObjectNameUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateClassService
        implements AddClassUseCase, ReplaceClassUseCase, DeleteClassUseCase, CopyClassUseCase {

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;
    private final ChangeLogUseCase changeLogUseCase;
    private final boolean newValuesAsBlankNode;

    private final CreateClassLayoutDataUseCase createClassLayoutDataUseCase;
    private final UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase;
    private final DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase;

    public UpdateClassService(
            DatabasePort databasePort,
            ClassUMLAdaptedMapper classMapper,
            PackageMapper packageMapper,
            ChangeLogUseCase changeLogUseCase,
            CreateClassLayoutDataUseCase createClassLayoutDataUseCase,
            UpdateDiagramObjectNameUseCase updateDiagramObjectNameUseCase,
            DeleteClassLayoutDataUseCase deleteClassLayoutDataUseCase,
            @Value("${attributes.newValuesBlankNode:false}") boolean newValuesAsBlankNode) {
        this.databasePort = databasePort;
        this.classMapper = classMapper;
        this.packageMapper = packageMapper;
        this.changeLogUseCase = changeLogUseCase;
        this.createClassLayoutDataUseCase = createClassLayoutDataUseCase;
        this.updateDiagramObjectNameUseCase = updateDiagramObjectNameUseCase;
        this.deleteClassLayoutDataUseCase = deleteClassLayoutDataUseCase;
        this.newValuesAsBlankNode = newValuesAsBlankNode;
    }

    @Override
    public void replaceClass(GraphIdentifier graphIdentifier, ClassUMLAdaptedDTO newClass) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            var cimClass = classMapper.toCIMObject(newClass);
            assertNoPackageWithSameIri(graph, cimClass);
            CIMUpdates.replaceClass(
                    graph,
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                    cimClass,
                    newValuesAsBlankNode);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        updateDiagramObjectNameUseCase.updateDiagramObjectName(
                graphIdentifier, newClass.getUuid(), newClass.getLabel());

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Updated class " + newClass.getUuid(), graph.getLastDelta()));
    }

    @Override
    public UUID addClass(
            GraphIdentifier graphIdentifier,
            PackageDTO packageDTO,
            String classURIPrefix,
            String className) {
        var cimPackage = packageMapper.toCIMObject(packageDTO);
        GraphRewindableWithUUIDs graph = null;
        UUID newClassUUID;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);

            var newClass = constructClass(cimPackage, classURIPrefix, className);
            assertNoPackageWithSameIri(graph, newClass);
            newClassUUID =
                    CIMUpdates.insertClass(
                            graph,
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            newClass);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        createClassLayoutDataUseCase.createClassLayoutData(
                graphIdentifier, packageDTO, className, newClassUUID);

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Added class " + className, graph.getLastDelta()));

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

    private void assertNoPackageWithSameIri(GraphRewindableWithUUIDs graph, CIMClass newClass) {
        var classUri = newClass.getUri().toNode();
        if (graph.contains(classUri, RDF.type.asNode(), CIMS.classCategory.asNode())) {
            throw new ResourceConflictException(
                    "Cannot save class "
                            + newClass.getUri()
                            + " because a package with the same IRI already exists.");
        }
    }

    @Override
    public void deleteClass(GraphIdentifier graphIdentifier, String classUUID) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            CIMUpdates.deleteClass(
                    graph, databasePort.getPrefixMapping(graphIdentifier.datasetName()), classUUID);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        deleteClassLayoutDataUseCase.deleteClassLayoutData(
                graphIdentifier, UUID.fromString(classUUID));

        changeLogUseCase.recordChange(
                graphIdentifier,
                new ChangeLogEntry("Deleted class: " + classUUID, graph.getLastDelta()));
    }

    @Override
    public UUID copyClass(
            GraphIdentifier graphIdentifier,
            UUID classUUID,
            GraphIdentifier targetGraphIdentifier,
            PackageDTO targetPackageDTO,
            boolean copyAsAbstract,
            boolean copyAttributes,
            boolean copyAssociations) {

        var cimClass = readSourceClass(graphIdentifier, classUUID.toString());

        var label =
                constructUniqueLabel(
                        cimClass.getLabel(),
                        getExistingClassLabels(
                                databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(),
                                graphIdentifier.graphUri(),
                                cimClass.getLabel()));

        var className = label.getValue();
        var cimPackage = packageMapper.toCIMObject(targetPackageDTO);
        var newCimClass =
                copyCimClass(
                        targetGraphIdentifier,
                        cimClass,
                        cimPackage,
                        label,
                        copyAsAbstract,
                        copyAttributes,
                        copyAssociations);

        var newClassUUID = insertClass(targetGraphIdentifier, newCimClass);

        changeLogUseCase.recordChange(
                targetGraphIdentifier,
                new ChangeLogEntry(
                        "Added class " + className,
                        databasePort
                                .getGraphWithContext(targetGraphIdentifier)
                                .getRdfGraph()
                                .getLastDelta()));
        return newClassUUID;
    }

    private CIMClassUMLAdapted readSourceClass(GraphIdentifier graphIdentifier, String classUUID) {
        GraphRewindableWithUUIDs graph = null;
        boolean ended = false;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            var cimClass =
                    CIMUMLObjectFactory.createCIMClassUMLAdapted(
                            graph,
                            graphIdentifier.graphUri(),
                            databasePort.getPrefixMapping(graphIdentifier.datasetName()),
                            classUUID);
            graph.end();
            ended = true;
            return cimClass;
        } finally {
            if (graph != null && !ended) {
                graph.end();
            }
        }
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
                        .superClass(cimClass.getSuperClass())
                        .stereotypes(stereotypes);
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
                copyAttributes
                        ? copyAttributes(cimClass.getAttributes(), newCimClass.build())
                        : List.of());
        newCimClass.enumEntries(
                copyAttributes
                        ? copyEnumEntries(cimClass.getEnumEntries(), newCimClass.build())
                        : List.of());
        newCimClass.associationPairs(
                copyAssociations
                        ? copyAssociations(
                                cimClass.getAssociationPairs(),
                                cimClass,
                                newCimClass.build(),
                                targetGraph,
                                targetGraphIdentifier.graphUri())
                        : List.of());

        return newCimClass.build();
    }

    private List<CIMAttribute> copyAttributes(List<CIMAttribute> attributes, CIMClass cimClass) {
        return attributes.stream()
                .map(
                        attr ->
                                attr.toBuilder()
                                        .uuid(UUID.randomUUID())
                                        .uri(
                                                new URI(
                                                        cimClass.getUri().getPrefix()
                                                                + cimClass.getUri().getSuffix()
                                                                + "."
                                                                + attr.getLabel().getValue()))
                                        .domain(
                                                new RDFSDomain(
                                                        cimClass.getUri(),
                                                        new RDFSLabel(
                                                                cimClass.getUri().getSuffix(),
                                                                "en")))
                                        .build())
                .toList();
    }

    private List<CIMEnumEntry> copyEnumEntries(List<CIMEnumEntry> enumEntries, CIMClass cimClass) {
        return enumEntries.stream()
                .map(
                        entry ->
                                entry.toBuilder()
                                        .uuid(UUID.randomUUID())
                                        .type(
                                                new RDFType(
                                                        cimClass.getUri(),
                                                        new RDFSLabel(
                                                                cimClass.getUri().getSuffix(),
                                                                "en")))
                                        .uri(
                                                new URI(
                                                        cimClass.getUri().getPrefix()
                                                                + cimClass.getUri().getSuffix()
                                                                + "."
                                                                + entry.getLabel().getValue()))
                                        .build())
                .toList();
    }

    private List<CIMAssociationPair> copyAssociations(
            List<CIMAssociationPair> pairs,
            CIMClass sourceClass,
            CIMClass newClass,
            GraphRewindableWithUUIDs graph,
            String graphUri) {
        return pairs.stream()
                .filter(pair -> pair.getFrom().getDomain().getUri().equals(sourceClass.getUri()))
                .map(
                        pair -> {
                            var existingLabels =
                                    getExistingAssociationLabels(
                                            graph,
                                            graphUri,
                                            pair.getTo().getDomain().getUri(),
                                            pair.getTo().getLabel());
                            var newToLabel =
                                    constructUniqueLabel(pair.getTo().getLabel(), existingLabels);
                            var from =
                                    pair.getFrom().toBuilder()
                                            .uuid(UUID.randomUUID())
                                            .uri(
                                                    new URI(
                                                            newClass.getUri()
                                                                    + "."
                                                                    + pair.getFrom()
                                                                            .getLabel()
                                                                            .getValue()))
                                            .domain(
                                                    new RDFSDomain(
                                                            newClass.getUri(),
                                                            new RDFSLabel(
                                                                    newClass.getUri().getSuffix(),
                                                                    "en")))
                                            .inverseRoleName(
                                                    new CIMSInverseRoleName(
                                                            pair.getFrom()
                                                                            .getRange()
                                                                            .getUri()
                                                                            .toString()
                                                                    + "."
                                                                    + newToLabel.getValue()))
                                            .build();

                            var to =
                                    pair.getTo().toBuilder()
                                            .uuid(UUID.randomUUID())
                                            .label(newToLabel)
                                            .uri(
                                                    new URI(
                                                            pair.getTo().getDomain()
                                                                    + "."
                                                                    + newToLabel.getValue()))
                                            .range(
                                                    new RDFSRange(
                                                            newClass.getUri(),
                                                            new RDFSLabel(
                                                                    newClass.getUri().getSuffix(),
                                                                    "en")))
                                            .inverseRoleName(
                                                    new CIMSInverseRoleName(
                                                            newClass.getUri().toString()
                                                                    + "."
                                                                    + from.getLabel().getValue()))
                                            .build();

                            return new CIMAssociationPair(from, to);
                        })
                .toList();
    }

    private Set<String> getExistingClassLabels(
            GraphRewindableWithUUIDs graph, String graphUri, RDFSLabel label) {
        var baseValue = label.getValue();

        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addGraph(
                                NodeFactory.createURI(graphUri),
                                new SelectBuilder()
                                        .addWhere("?s", RDFS.label, "?label")
                                        .addFilter(
                                                exprFactory.strstarts(
                                                        exprFactory.str("?label"), baseValue)))
                        .build();
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graph, query, graphUri);

        var existingLabels = new HashSet<String>();
        while (resultSet.hasNext()) {
            var solution = resultSet.nextSolution();
            existingLabels.add(solution.getLiteral("label").getString());
        }
        return existingLabels;
    }

    private Set<String> getExistingAssociationLabels(
            GraphRewindableWithUUIDs graph, String graphUri, URI domainUri, RDFSLabel label) {
        var exprFactory = new ExprFactory();
        var query =
                new SelectBuilder()
                        .addVar("?label")
                        .addGraph(
                                NodeFactory.createURI(graphUri),
                                new SelectBuilder()
                                        .addWhere(
                                                "?s",
                                                RDFS.domain,
                                                NodeFactory.createURI(domainUri.toString()))
                                        .addWhere("?s", RDFS.label, "?label")
                                        .addFilter(
                                                exprFactory.strstarts(
                                                        exprFactory.str("?label"),
                                                        label.getValue())))
                        .build();
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graph, query, graphUri);
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
        baseValue = label.getValue() + " - Copy";
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
