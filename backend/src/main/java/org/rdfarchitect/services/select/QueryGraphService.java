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

package org.rdfarchitect.services.select;

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Node;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.cim.CIMQuerySolutionParser;
import org.rdfarchitect.cim.data.CIMObjectFactory;
import org.rdfarchitect.cim.data.dto.CIMPackage;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.queries.CIMQueryVars;
import org.rdfarchitect.cim.queries.select.CIMBaseQueryBuilder;
import org.rdfarchitect.cim.queries.select.CIMQueryBuilder;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.cim.umladapted.CIMUMLObjectFactory;
import org.rdfarchitect.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutioner;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.rdf.model.wrapper.CimSortedModel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.rdfarchitect.cim.queries.select.CIMQueryBuilder.Mode.*;
import static org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs.*;

@Service
@RequiredArgsConstructor
public class QueryGraphService implements GetClassListUseCase, ListDatatypesUseCase, GetSchemaUseCase, ListInternalPackagesUseCase, ListExternalPackagesUseCase,
          ListPrimitivesUseCase, ListStereotypesUseCase, ResolveIdentifierUseCase {

    private static final String BLANK_PACKAGE_NAME = "default";
    private static final String BLANK_PACKAGE_LANG = "en";

    private final DatabasePort databasePort;
    private final ClassUMLAdaptedMapper classMapper;
    private final PackageMapper packageMapper;

    @Override
    public List<ClassUMLAdaptedDTO> getClassList(GraphIdentifier graphIdentifier) {
        //build query
        var baseQuery = new CIMBaseQueryBuilder()
                  .setGraph(graphIdentifier.getGraphUri())
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setOrder()
                  .setDistinct()
                  .setType(RDFS.Class)
                  .build();
        var query = new CIMQueryBuilder(baseQuery)
                  .appendUUIDQuery(OPTIONAL)
                  .appendLabelQuery(REQUIRED)
                  .appendPackageQuery(OPTIONAL)
                  .appendCommentQuery(OPTIONAL)
                  .appendSuperClassQuery(OPTIONAL)
                  .build();

        //execute query
        var queryResultSet = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), query, graphIdentifier.getGraphUri());

        //format results
        var cimClassList = CIMUMLObjectFactory.createCIMClassUMLAdaptedList(queryResultSet);
        cimClassList.forEach(CIMClassUMLAdapted::nullEmptyLists);

        return classMapper.toDTOList(cimClassList);
    }

    @Override
    public List<ClassUMLAdaptedDTO> listDatatypes(GraphIdentifier graphIdentifier) {
        //build query
        var baseQuery = new CIMBaseQueryBuilder()
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setGraph(graphIdentifier.getGraphUri())
                  .setOrder()
                  .setDistinct()
                  .setType(RDFS.Class)
                  .filterStereotypes(CIMStereotypes.enumeration.getURI(), "Entsoe")
                  .build();
        var query = new CIMQueryBuilder(baseQuery)
                  .appendLabelQuery(OPTIONAL)
                  .appendPackageQuery(OPTIONAL)
                  .appendCommentQuery(OPTIONAL)
                  .appendSuperClassQuery(OPTIONAL)
                  .build();

        //execute query
        var queryResultSet = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), query, graphIdentifier.getGraphUri());

        //format results
        var cimClassList = CIMUMLObjectFactory.createCIMClassUMLAdaptedList(queryResultSet);
        cimClassList.forEach(CIMClassUMLAdapted::nullEmptyLists);

        return classMapper.toDTOList(cimClassList);
    }

    @Override
    public ByteArrayOutputStream getSchema(GraphIdentifier graphIdentifier, RDFFormat format) {
        GraphRewindableWithUUIDs graph = null;
        try (var out = new ByteArrayOutputStream()) {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            var copiedGraph = GraphUtils.deepCopy(graph);
            copiedGraph.getPrefixMapping().setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()));
            removeUUIDs(copiedGraph);
            var sortedModel = new CimSortedModel(ModelFactory.createModelForGraph(copiedGraph));
            sortedModel.write(out, format.getLang().getName());
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    @Override
    public List<PackageDTO> listInternalPackages(GraphIdentifier graphIdentifier) {
        //build package query
        var internalPackageBaseQuery = new CIMBaseQueryBuilder()
                  .setDistinct()
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setGraph(graphIdentifier.getGraphUri())
                  .setType(CIMS.classCategory)
                  .build();

        var internalPackageQuery = new CIMQueryBuilder(internalPackageBaseQuery)
                  .appendUUIDQuery(REQUIRED)
                  .appendLabelQuery(REQUIRED)
                  .appendPackageQuery(OPTIONAL)
                  .appendCommentQuery(OPTIONAL)
                  .build();

        //execute package query
        var internalPackageQueryResultSet =
                  InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier)
                                                                           .getRdfGraph(), internalPackageQuery, graphIdentifier.getGraphUri());

        //format results
        var cimPackageList = CIMObjectFactory.createCIMPackageList(internalPackageQueryResultSet);

        //add blank package
        URI uri = new URI(BLANK_PACKAGE_NAME);
        var blankPackage = CIMPackage.builder()
                                     .uri(uri)
                                     .label(new RDFSLabel(BLANK_PACKAGE_NAME, BLANK_PACKAGE_LANG))
                                     .build();
        cimPackageList.add(blankPackage);

        return packageMapper.toDTOList(cimPackageList);
    }

    @Override
    public List<PackageDTO> listExternalPackages(GraphIdentifier graphIdentifier) {
        //build external package query
        var externalPackageBaseQuery = new CIMBaseQueryBuilder()
                  .setDistinct()
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setGraph(graphIdentifier.getGraphUri())
                  .addWhereThisNotExists(RDF.type.getURI(), CIMS.classCategory.getURI())
                  .build()
                  .addWhere(Node.ANY, CIMS.belongsToCategory.asNode(), CIMQueryVars.URI);

        var externalPackageQuery = new CIMQueryBuilder(externalPackageBaseQuery)
                  .appendUUIDQuery(REQUIRED)
                  .build();

        //execute external package query
        var externalPackageQueryResultSet =
                  InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier)
                                                                           .getRdfGraph(), externalPackageQuery, graphIdentifier.getGraphUri());

        var cimExternalPackageList = CIMObjectFactory.createExternalCIMPackageList(externalPackageQueryResultSet);

        return packageMapper.toDTOList(cimExternalPackageList);
    }

    @Override
    public List<ClassUMLAdaptedDTO> listPrimitives(GraphIdentifier graphIdentifier) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .setOrder()
                  .setDistinct()
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getGraphUri()))
                  .filterStereotypes(CIMStereotypes.primitiveString, CIMStereotypes.cimDatatypeString)
                  .setGraph(graphIdentifier.getGraphUri())
                  .setType(RDFS.Class)
                  .build();
        var query = new CIMQueryBuilder(baseQuery)
                  .appendLabelQuery(REQUIRED)
                  .appendPackageQuery(OPTIONAL)
                  .appendCommentQuery(OPTIONAL)
                  .appendSuperClassQuery(OPTIONAL)
                  .build();

        //execute query
        var queryResultSet = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), query, graphIdentifier.getGraphUri());

        //format results
        var cimClassList = CIMUMLObjectFactory.createCIMClassUMLAdaptedList(queryResultSet);
        cimClassList.forEach(CIMClassUMLAdapted::nullEmptyLists);

        return classMapper.toDTOList(cimClassList);
    }

    @Override
    public List<CIMSStereotype> listStereotypes(GraphIdentifier graphIdentifier) {
        var baseQuery = new CIMBaseQueryBuilder()
                  .setOrder()
                  .setDistinct()
                  .addPrefixes(databasePort.getPrefixMapping(graphIdentifier.getDatasetName()))
                  .setGraph(graphIdentifier.getGraphUri())
                  .buildWithoutUriVar();

        var query = new CIMQueryBuilder(baseQuery)
                  .appendStereotypeQuery(REQUIRED)
                  .build();


        //execute query
        var queryResult = InMemorySparqlExecutioner.executeSingleQuery(databasePort.getGraphWithContext(graphIdentifier).getRdfGraph(), query, graphIdentifier.getGraphUri());

        //format results
        List<CIMSStereotype> resultList = new ArrayList<>();
        while (queryResult.hasNext()) {
            var parser = new CIMQuerySolutionParser(queryResult.next());
            resultList.add(parser.getStereotype(CIMQueryVars.STEREOTYPE));
        }

        return resultList;
    }

    @Override
    public UUID resolveIRI(GraphIdentifier graphIdentifier, String resourceIRI) {
        GraphRewindableWithUUIDs graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            var model = ModelFactory.createModelForGraph(graph);
            var resource = model.getResource(resourceIRI);
            if (resource == null || !model.contains(resource, RDFA.uuid)) {
                throw new DataAccessException("Resource with IRI " + resourceIRI + " not found.");
            }
            var uuidLiteral = model.getProperty(resource, RDFA.uuid).getObject().asLiteral();
            return UUID.fromString(uuidLiteral.getString());
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }
}
