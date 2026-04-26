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

package org.rdfarchitect.services;

import static org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder.Mode;

import lombok.RequiredArgsConstructor;

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.models.cim.CIMQuerySolutionParser;
import org.rdfarchitect.models.cim.data.CIMObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.queries.select.CIMBaseQueryBuilder;
import org.rdfarchitect.models.cim.queries.select.CIMQueries;
import org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Implementation of {@link GraphToCIMCollectionConverterUseCase}. */
@Service
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class GraphToCIMCollectionConverterService implements GraphToCIMCollectionConverterUseCase {

    private final DatabasePort databasePort;

    @Override
    public CIMCollection convert(GraphIdentifier graphIdentifier, GraphFilter filter) {
        var cimCollection = new CIMCollection();
        Graph copiedGraph;
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            copiedGraph = GraphUtils.deepCopy(graph);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }

        fetchAllPackages(copiedGraph, graphIdentifier, cimCollection);
        fetchClasses(copiedGraph, graphIdentifier, filter, cimCollection);
        fetchEnums(copiedGraph, graphIdentifier, filter, cimCollection);
        fetchAssociations(copiedGraph, graphIdentifier, filter, cimCollection);
        return cimCollection;
    }

    private CIMBaseQueryBuilder buildBaseQuery(GraphIdentifier graphIdentifier) {
        return new CIMBaseQueryBuilder()
                .setGraph(graphIdentifier.getGraphUri())
                .setDistinct()
                .setOrder()
                .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));
    }

    private void fetchAllPackages(
            Graph graph, GraphIdentifier graphIdentifier, CIMCollection cimCollection) {
        fetchInternalPackages(graph, graphIdentifier, cimCollection);
        fetchExternalPackages(graph, graphIdentifier, cimCollection);
    }

    private void fetchInternalPackages(
            Graph graph, GraphIdentifier graphIdentifier, CIMCollection cimCollection) {
        var internalPackagesQuery =
                new CIMQueryBuilder(buildBaseQuery(graphIdentifier).build())
                        .appendUUIDQuery(Mode.REQUIRED)
                        .appendLabelQuery(Mode.REQUIRED)
                        .appendCommentQuery(Mode.OPTIONAL)
                        .buildSelectBuilder()
                        .addWhere(CIMQueryVars.URI, RDF.type, CIMS.classCategory)
                        .build();

        // execute query
        var dataset = SessionDataStore.wrapGraphInDataset(graph, graphIdentifier.getGraphUri());
        try (var qexec = QueryExecutionFactory.create(internalPackagesQuery, dataset)) {
            var resultset = qexec.execSelect();
            while (resultset.hasNext()) {
                var solutionParser = new CIMQuerySolutionParser(resultset.next());
                var cimPackage =
                        CIMPackage.builder()
                                .uuid(solutionParser.getUUID(CIMQueryVars.UUID))
                                .uri(solutionParser.getURI(CIMQueryVars.URI))
                                .label(solutionParser.getLabel(CIMQueryVars.LABEL))
                                .comment(solutionParser.getComment(CIMQueryVars.COMMENT))
                                .build();
                cimCollection.getPackages().add(cimPackage);
            }
        }
    }

    private void fetchExternalPackages(
            Graph graph, GraphIdentifier graphIdentifier, CIMCollection cimCollection) {
        var externalPackagesBaseQuery =
                buildBaseQuery(graphIdentifier)
                        .addWhereThisNotExists(RDF.type.getURI(), CIMS.classCategory.getURI())
                        .build()
                        .addWhere(Node.ANY, CIMS.belongsToCategory, CIMQueryVars.URI);
        var externalPackagesQuery =
                new CIMQueryBuilder(externalPackagesBaseQuery)
                        .appendUUIDQuery(Mode.REQUIRED)
                        .appendLabelQuery(Mode.OPTIONAL)
                        .appendCommentQuery(Mode.OPTIONAL)
                        .build();

        // execute query
        var dataset = SessionDataStore.wrapGraphInDataset(graph, graphIdentifier.getGraphUri());
        try (var qexec = QueryExecutionFactory.create(externalPackagesQuery, dataset)) {
            var resultset = qexec.execSelect();
            while (resultset.hasNext()) {
                var solutionParser = new CIMQuerySolutionParser(resultset.next());
                var cimPackage =
                        CIMPackage.builder()
                                .uuid(solutionParser.getUUID(CIMQueryVars.UUID))
                                .uri(solutionParser.getURI(CIMQueryVars.URI))
                                .build();
                cimPackage.setLabel(new RDFSLabel(cimPackage.getUri().getSuffix()));

                cimCollection.getPackages().add(cimPackage);
            }
        }
    }

    // classes
    private void fetchClasses(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        fetchClassesInPackage(graph, graphIdentifier, filter, cimCollection);
        fetchExternalAssociatedClasses(graph, graphIdentifier, filter, cimCollection);
        fetchExternallyInheritanceRelatedClasses(graph, graphIdentifier, filter, cimCollection);

        clearSuperClassRelations(filter, cimCollection);
        clearSuperClassRelationsToExternalClasses(filter, cimCollection);
        clearInheritanceToNonExistingClasses(cimCollection);
    }

    private void clearSuperClassRelations(GraphFilter filter, CIMCollection cimCollection) {
        if (filter.isIncludeInheritance()) {
            return;
        }
        for (var cimClass : cimCollection.getClasses()) {
            cimClass.setSuperClass(null);
        }
    }

    private void clearSuperClassRelationsToExternalClasses(
            GraphFilter filter, CIMCollection cimCollection) {
        if (filter.isIncludeInheritance() && filter.isIncludeRelationsToExternalPackages()) {
            return;
        }
        // remove superClass relation of classes that have a superclass that is not in the package
        for (var cimClass : cimCollection.getClasses()) {
            if (cimClass.getSuperClass() != null
                    && (cimCollection.getClasses().stream()
                                    .noneMatch(
                                            otherClass ->
                                                    otherClass
                                                            .getUri()
                                                            .equals(
                                                                    cimClass.getSuperClass()
                                                                            .getUri()))
                            || !isClassInPackage(
                                    cimClass.getSuperClass().getUri().toString(), cimCollection))) {
                cimClass.setSuperClass(null);
            }
        }
    }

    private void clearInheritanceToNonExistingClasses(CIMCollection cimCollection) {
        for (var cimClass : cimCollection.getClasses()) {
            if (cimClass.getSuperClass() != null
                    && cimCollection.getClasses().stream()
                            .noneMatch(
                                    otherClass ->
                                            otherClass
                                                    .getUri()
                                                    .equals(cimClass.getSuperClass().getUri()))) {
                cimClass.setSuperClass(null);
            }
        }
    }

    private boolean isClassInPackage(String classUri, CIMCollection cimCollection) {
        return cimCollection.getClasses().stream()
                .anyMatch(cimClass -> cimClass.getUri().toString().equals(classUri));
    }

    private void fetchClasses(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection,
            SelectBuilder query) {
        query.addFilter(
                "NOT EXISTS{ "
                        + CIMQueryVars.URI
                        + " "
                        + CIMQueryVars.STEREOTYPE
                        + " <"
                        + CIMStereotypes.enumeration
                        + "> }");
        var classList =
                new CIMObjectFetcher(
                                graph,
                                graphIdentifier.getGraphUri(),
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                        .fetchCIMClassList(query.build());
        var classUUIDList = new ArrayList<String>();
        classList.forEach(
                cimClass -> {
                    if (cimCollection.getClasses().stream()
                            .noneMatch(
                                    otherClass ->
                                            cimClass.getUuid().equals(otherClass.getUuid()))) {
                        cimCollection.getClasses().add(cimClass);
                        classUUIDList.add(cimClass.getUuid().toString());
                    }
                });
        fetchAttributes(graph, graphIdentifier, filter, cimCollection, classUUIDList);
    }

    private void fetchClassesInPackage(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        // classQuery
        var classesInPackageQueryBuilder =
                CIMQueries.getClassQuery(
                        databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                        graphIdentifier.getGraphUri(),
                        null);
        appendPackageConstraint(filter, classesInPackageQueryBuilder, CIMQueryVars.URI, true);
        fetchClasses(graph, graphIdentifier, filter, cimCollection, classesInPackageQueryBuilder);
    }

    private void fetchExternalAssociatedClasses(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        if (!filter.isIncludeRelationsToExternalPackages() || !filter.isIncludeAssociations()) {
            return;
        }
        // classQuery
        var inPackageClassUri = "?inPackageClassUri";
        var associationUri = "?associationUri";
        var associatedClassesQueryBuilder =
                CIMQueries.getClassQuery(
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                                graphIdentifier.getGraphUri(),
                                null)
                        .addWhere(inPackageClassUri, RDF.type, RDFS.Class)
                        .addWhere(associationUri, RDF.type, RDF.Property)
                        .addWhere(
                                associationUri,
                                CIMS.associationUsed,
                                "?_") // every association has this triple
                        .addWhere(associationUri, RDFS.domain, inPackageClassUri)
                        .addWhere(associationUri, RDFS.range, CIMQueryVars.URI);
        appendPackageConstraint(filter, associatedClassesQueryBuilder, inPackageClassUri, true);
        appendPackageConstraint(filter, associatedClassesQueryBuilder, CIMQueryVars.URI, false);
        fetchClasses(graph, graphIdentifier, filter, cimCollection, associatedClassesQueryBuilder);
    }

    private void fetchExternallyInheritanceRelatedClasses(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        if (!filter.isIncludeRelationsToExternalPackages() || !filter.isIncludeInheritance()) {
            return;
        }
        // classQuery
        var inPackageClassUri = "?inPackageClassUri";
        var associatedClassesQueryBuilder =
                CIMQueries.getClassQuery(
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                                graphIdentifier.getGraphUri(),
                                null)
                        .addWhere(inPackageClassUri, RDF.type, RDFS.Class)
                        .addWhere(
                                new SelectBuilder()
                                        .addWhere(
                                                CIMQueryVars.URI,
                                                RDFS.subClassOf,
                                                inPackageClassUri)
                                        .addUnion(
                                                new SelectBuilder()
                                                        .addWhere(
                                                                inPackageClassUri,
                                                                RDFS.subClassOf,
                                                                CIMQueryVars.URI)));
        appendPackageConstraint(filter, associatedClassesQueryBuilder, inPackageClassUri, true);
        appendPackageConstraint(filter, associatedClassesQueryBuilder, CIMQueryVars.URI, false);
        fetchClasses(graph, graphIdentifier, filter, cimCollection, associatedClassesQueryBuilder);
    }

    private void fetchAttributes(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection,
            List<String> classUUIDList) {
        if (!filter.isIncludeAttributes() || classUUIDList.isEmpty()) {
            return;
        }
        var attributesQuery = buildBaseQuery(graphIdentifier).buildWithoutUriVar();
        for (String classUUID : classUUIDList) {
            var attributeQuery =
                    CIMQueries.getAttributesQuery(
                            databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                            classUUID,
                            graphIdentifier.getGraphUri());
            attributesQuery.addUnion(attributeQuery);
        }
        var attributeList =
                new CIMObjectFetcher(
                                graph,
                                graphIdentifier.getGraphUri(),
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                        .fetchCIMAttributeList(attributesQuery.build());

        attributeList.forEach(cimAttribute -> cimCollection.getAttributes().add(cimAttribute));
    }

    // enums
    private void fetchEnums(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        var enumsInPackageQueryBuilder =
                CIMQueries.getEnumClassesQuery(
                        databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                        graphIdentifier.getGraphUri(),
                        null);
        appendPackageConstraint(filter, enumsInPackageQueryBuilder, CIMQueryVars.URI, true);
        var enumList =
                new CIMObjectFetcher(
                                graph,
                                graphIdentifier.getGraphUri(),
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                        .fetchCIMClassList(enumsInPackageQueryBuilder.build());
        enumList.forEach(cimEnum -> cimCollection.getEnums().add(cimEnum));
        // fetch enum entries
        var enumUUIDList = new ArrayList<String>();
        enumList.forEach(cimEnum -> enumUUIDList.add(cimEnum.getUuid().toString()));
        fetchEnumEntries(graph, graphIdentifier, filter, cimCollection, enumUUIDList);
    }

    private void fetchEnumEntries(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection,
            List<String> enumUUIDList) {
        if (!filter.isIncludeEnumEntries() || enumUUIDList.isEmpty()) {
            return;
        }
        var enumEntriesQuery = buildBaseQuery(graphIdentifier).buildWithoutUriVar();
        for (String enumUUID : enumUUIDList) {
            var enumEntryQuery =
                    CIMQueries.getEnumEntriesQuery(
                            databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                            graphIdentifier.getGraphUri(),
                            enumUUID);
            enumEntriesQuery.addUnion(enumEntryQuery);
        }

        var enumEntryList =
                new CIMObjectFetcher(
                                graph,
                                graphIdentifier.getGraphUri(),
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                        .fetchCIMEnumEntryList(enumEntriesQuery.build());

        enumEntryList.forEach(cimEnumEntry -> cimCollection.getEnumEntries().add(cimEnumEntry));
    }

    private void fetchAssociations(
            Graph graph,
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            CIMCollection cimCollection) {
        if (!filter.isIncludeAssociations() || cimCollection.getClasses().isEmpty()) {
            return;
        }
        var associationsQuery = buildBaseQuery(graphIdentifier).buildWithoutUriVar();
        for (CIMClass cimClass : cimCollection.getClasses()) {
            var associationPairQuery =
                    CIMQueries.getAssociationsQuery(
                            databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                            graphIdentifier.getGraphUri(),
                            cimClass.getUuid().toString());
            associationsQuery.addUnion(associationPairQuery);
        }

        for (CIMClass cimEnum : cimCollection.getEnums()) {
            var associationPairQuery =
                    CIMQueries.getAssociationsQuery(
                            databasePort.getPrefixMapping(graphIdentifier.getDatasetName()),
                            graphIdentifier.getGraphUri(),
                            cimEnum.getUuid().toString());
            associationsQuery.addUnion(associationPairQuery);
        }

        var associationList =
                new CIMObjectFetcher(
                                graph,
                                graphIdentifier.getGraphUri(),
                                databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                        .fetchCIMAssociationList(associationsQuery.build());

        associationList.forEach(
                cimAssociation -> {
                    if (cimCollection.getClasses().stream()
                            .anyMatch(
                                    cimClass ->
                                            cimClass.getUri()
                                                    .equals(cimAssociation.getRange().getUri()))) {
                        cimCollection.getAssociations().add(cimAssociation);
                    }
                });
    }

    /**
     * Appends a package constraint to the given {@link SelectBuilder}.
     *
     * @param filter The {@link GraphFilter} to get the package URI from.
     * @param selectBuilder The {@link SelectBuilder} to append the constraint to.
     * @param subjectUri The URI of the subject to apply the constraint to.
     * @param subjectIsInPackage Whether the subject should be included or excluded from the
     *     package.
     */
    private void appendPackageConstraint(
            GraphFilter filter,
            SelectBuilder selectBuilder,
            String subjectUri,
            boolean subjectIsInPackage) {
        boolean isDefaultPackage =
                filter.getPackageUUID() == null || filter.getPackageUUID().equals("default");
        var tempPackageUri = "?tempPackageUri";
        if (subjectIsInPackage) {
            if (isDefaultPackage) {
                selectBuilder.addFilter(
                        "NOT EXISTS{ "
                                + subjectUri
                                + " <"
                                + CIMS.belongsToCategory.toString()
                                + "> ?_anyPackage1 }");
                return;
            }
            selectBuilder.addWhere(tempPackageUri, RDFA.uuid, filter.getPackageUUID());
            selectBuilder.addWhere(subjectUri, CIMS.belongsToCategory, tempPackageUri);
            return;
        }
        if (isDefaultPackage) {
            selectBuilder.addWhere(subjectUri, CIMS.belongsToCategory, "?_anyPackage2");
            return;
        }
        selectBuilder.addWhere(tempPackageUri, RDFA.uuid, filter.getPackageUUID());
        selectBuilder.addFilter(
                "NOT EXISTS{ "
                        + subjectUri
                        + " <"
                        + CIMS.belongsToCategory
                        + "> \""
                        + tempPackageUri
                        + "\" }");
    }
}
