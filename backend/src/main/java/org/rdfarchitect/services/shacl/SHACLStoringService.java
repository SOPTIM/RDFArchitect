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

package org.rdfarchitect.services.shacl;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.merge.ModelResourceExclusiveMerge;
import org.rdfarchitect.shacl.PropertyShapeToClassAssigner;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.SHACLShapesFetcher;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This implementation is able to store a single shacl file. This is a temporary solution missing
 * the core concept of storing multiple shacl files.
 */
@RequiredArgsConstructor
public class SHACLStoringService
        implements SHACLInsertUseCase,
                SHACLExportUseCase,
                SHACLGetClassRelationsUseCase,
                SHACLGetShapeUseCase,
                SHACLReplaceShapeUseCase,
                SHACLDeleteShapeUseCase,
                SHACLUpdateUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SHACLStoringService.class);

    public static final PrefixEntry SHACL_NAMESPACE =
            PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL);

    private final DatabasePort databasePort;

    @Override
    public void replaceCustomSHACLGraph(GraphIdentifier graphIdentifier, Graph shacl) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var storedGraph = ctx.getCustomSHACL();
            storedGraph.clear();
            var storedModel = ModelFactory.createModelForGraph(storedGraph);
            var newModel = ModelFactory.createModelForGraph(GraphUtils.normalizeBlankNodes(shacl));
            storedModel.clearNsPrefixMap();
            storedModel.add(newModel);
            storedModel.setNsPrefixes(newModel);

            ctx.commit("Replace custom SHACL");
        }
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            try (var outStream = new ByteArrayOutputStream()) {
                customSHACL.write(outStream, format.getLang().getName());
                return outStream;
            } catch (Exception e) {
                logger.warn("Error while writing SHACL graph to output stream", e);
                return new ByteArrayOutputStream();
            }
        }
    }

    @Override
    public ByteArrayOutputStream exportGeneratedSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ);
                var outStream = new ByteArrayOutputStream()) {
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();

            generatedShacl.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing SHACL graph to output stream", e);
        }
    }

    @Override
    public ByteArrayOutputStream exportGeneratedSHACLGraph(Graph graph, RDFFormat format) {
        var ontologyModel = ModelFactory.createModelForGraph(graph);
        var generatedShacl =
                new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();
        try (var outStream = new ByteArrayOutputStream()) {
            generatedShacl.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing shacl graph to output stream", e);
        }
    }

    @Override
    public ByteArrayOutputStream exportCombinedSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();

            var mergedModel = new ModelResourceExclusiveMerge().merge(customSHACL, generatedShacl);
            try (var outStream = new ByteArrayOutputStream()) {
                mergedModel.write(outStream, format.getLang().getName());
                return outStream;
            } catch (IOException e) {
                throw new DataAccessException(
                        "Error while writing combined shacl graph to output stream", e);
            }
        }
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLNamespaces(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            try (var outStream = new ByteArrayOutputStream()) {
                var prefixModel = ModelFactory.createDefaultModel();
                prefixModel.setNsPrefixes(customSHACL.getNsPrefixMap());
                prefixModel.write(outStream, format.getLang().getName());
                return outStream;
            } catch (Exception e) {
                logger.warn("Error while writing SHACL prefixes to output stream", e);
                return new ByteArrayOutputStream();
            }
        }
    }

    @Override
    public ByteArrayOutputStream exportGeneratedSHACLNamespaces(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var outStream = new ByteArrayOutputStream()) {
            var prefixModel = ModelFactory.createDefaultModel();
            prefixModel.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            prefixModel.setNsPrefix(SHACL_NAMESPACE.getPrefix(), SHACL_NAMESPACE.getUri());
            prefixModel.write(outStream, format.getLang().getName());
            return outStream;
        } catch (Exception e) {
            logger.warn("Error while writing SHACL prefixes to output stream", e);
            return new ByteArrayOutputStream();
        }
    }

    @Override
    public CustomAndGeneratedTuple<SHACLToClassRelations> getSHACLToClassRelations(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedSHACL =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(classUUID);
            var shaclResult = new CustomAndGeneratedTuple<SHACLToClassRelations>();
            shaclResult.setCustom(getSHACLToClassRelations(ontologyModel, customSHACL, classUUID));
            shaclResult.setGenerated(
                    getSHACLToClassRelations(ontologyModel, generatedSHACL, classUUID));
            return shaclResult;
        }
    }

    private SHACLToClassRelations getSHACLToClassRelations(
            Model ontologyModel, Model shaclModel, UUID classUUID) {
        var classUri =
                ontologyModel
                        .listSubjectsWithProperty(
                                RDFA.uuid, ontologyModel.createLiteral(classUUID.toString()))
                        .next()
                        .getURI();
        var prefixMapping = new PrefixMappingImpl();
        prefixMapping.setNsPrefixes(shaclModel.getNsPrefixMap());
        var shaclShapesFetcher = new SHACLShapesFetcher(shaclModel);
        var shaclToClassAssigner = new PropertyShapeToClassAssigner(shaclModel, ontologyModel);
        return SHACLToClassRelations.builder()
                .namespaces(prefixMappingToTtlString(prefixMapping))
                .nodeShapes(shaclShapesFetcher.getNodeShapesOfClass(classUri))
                .propertyShapes(shaclToClassAssigner.getPropertyShapes(classUUID))
                .derivedPropertyShapes(
                        shaclToClassAssigner.getDerivedPropertyShapesOfClass(classUUID))
                .build();
    }

    private String prefixMappingToTtlString(PrefixMapping prefixMapping) {
        var model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(prefixMapping);
        var stream = new ByteArrayOutputStream();
        model.write(stream, Lang.TTL.getName());
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public CustomAndGeneratedTuple<List<PropertyShape>> getPropertyShapesForAttribute(
            GraphIdentifier graphIdentifier, UUID attributeUUID) {
        return getSHACLShapesByProperty(graphIdentifier, attributeUUID);
    }

    @Override
    public CustomAndGeneratedTuple<List<PropertyShape>> getPropertyShapesForAssociation(
            GraphIdentifier graphIdentifier, UUID associationUUID) {
        return getSHACLShapesByProperty(graphIdentifier, associationUUID);
    }

    private CustomAndGeneratedTuple<List<PropertyShape>> getSHACLShapesByProperty(
            GraphIdentifier graphIdentifier, UUID propertyUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var property =
                    ontologyModel
                            .listSubjectsWithProperty(
                                    RDFA.uuid, ontologyModel.createLiteral(propertyUUID.toString()))
                            .next();
            var classUUID =
                    property.getProperty(RDFS.domain)
                            .getProperty(RDFA.uuid)
                            .getLiteral()
                            .getString();
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(UUID.fromString(classUUID));

            List<PropertyShape> customPropertyShapes =
                    getCustomPropertyShapesOfProperty(ontologyModel, customSHACL, propertyUUID);
            var generatedPropertyShapes =
                    new SHACLShapesFetcher(generatedShacl)
                            .getPropertyShapesOfProperty(ontologyModel, property.getURI());
            return new CustomAndGeneratedTuple<List<PropertyShape>>()
                    .setCustom(customPropertyShapes)
                    .setGenerated(generatedPropertyShapes);
        }
    }

    /**
     * Retrieves the custom property shapes for a given property UUID without managing its own
     * transaction. This allows callers to use it within an existing transaction context.
     *
     * @param ontologyModel the ontology model
     * @param customSHACL the model containing the custom SHACL shapes
     * @param propertyUUID the property UUID
     * @return a list of custom property shapes for the given property
     */
    private List<PropertyShape> getCustomPropertyShapesOfProperty(
            Model ontologyModel, Model customSHACL, UUID propertyUUID) {
        var property =
                ontologyModel
                        .listSubjectsWithProperty(
                                RDFA.uuid, ontologyModel.createLiteral(propertyUUID.toString()))
                        .next();
        return new SHACLShapesFetcher(customSHACL)
                .getPropertyShapesOfProperty(ontologyModel, property.getURI());
    }

    @Override
    public CustomAndGeneratedTuple<List<NodeShape>> getNodeShapesForClass(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var classUri =
                    ontologyModel
                            .listSubjectsWithProperty(
                                    RDFA.uuid, ontologyModel.createLiteral(classUUID.toString()))
                            .next()
                            .getURI();
            var customNodeShapes =
                    new SHACLShapesFetcher(customSHACL).getNodeShapesOfClass(classUri);
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(classUUID);
            var generatedNodeShapes =
                    new SHACLShapesFetcher(generatedShacl).getNodeShapesOfClass(classUri);
            return new CustomAndGeneratedTuple<List<NodeShape>>()
                    .setCustom(customNodeShapes)
                    .setGenerated(generatedNodeShapes);
        }
    }

    @Override
    public List<PropertyShapesWrapper> getPropertyShapes(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var shaclToClassAssigner =
                    new PropertyShapeToClassAssigner(
                            customSHACL, ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            return shaclToClassAssigner.getPropertyShapes(classUUID);
        }
    }

    @Override
    public void deleteSHACLShape(GraphIdentifier graphIdentifier, String shaclShapeURI) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var deleteModel = ModelFactory.createDefaultModel();
            copySHACLShapeToNewModel(
                    customSHACL, deleteModel, ResourceFactory.createResource(shaclShapeURI));
            customSHACL.remove(deleteModel);
            ctx.commit("Delete SHACL shape");
        }
    }

    @Override
    public void replaceSHACLShape(
            GraphIdentifier graphIdentifier, String shaclShapeURI, String shaclToInsert) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            Model insertModel = parseTriplesToModel(shaclToInsert);
            Model deleteModel = ModelFactory.createDefaultModel();
            copySHACLShapeToNewModel(
                    customSHACL, deleteModel, ResourceFactory.createResource(shaclShapeURI));
            customSHACL.remove(deleteModel);
            customSHACL.add(insertModel);
            ctx.commit("Replace SHACL shape");
        }
    }

    private Model parseTriplesToModel(String triples) {
        if (triples.trim().isEmpty()) {
            return ModelFactory.createDefaultModel();
        }
        Model model = ModelFactory.createDefaultModel();
        try (StringReader reader = new StringReader(triples)) {
            return model.read(reader, null, "TURTLE");
        }
    }

    @Override
    public void updateClassSHACL(
            GraphIdentifier graphIdentifier, UUID classUUID, String ttlShaclString) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var insertModel = parseTriplesToModel(ttlShaclString);
            var deleteModel = getClassShaclModel(ontologyModel, customSHACL, classUUID);

            customSHACL.remove(deleteModel);
            customSHACL.clearNsPrefixMap();
            customSHACL.setNsPrefixes(insertModel);
            customSHACL.add(insertModel);
            ctx.commit("Update class SHACL");
        }
    }

    @Override
    public void updatePropertyShacl(
            GraphIdentifier graphIdentifier, UUID propertyUUID, String ttlShaclString) {
        var insertModel = parseTriplesToModel(ttlShaclString);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());

            var customPropertyShapes =
                    getCustomPropertyShapesOfProperty(ontologyModel, customSHACL, propertyUUID);

            var deleteModel = ModelFactory.createDefaultModel();
            for (var propertyShape : customPropertyShapes) {
                copySHACLShapeToNewModel(
                        customSHACL,
                        deleteModel,
                        ResourceFactory.createResource(propertyShape.getId()));
            }
            customSHACL.remove(deleteModel);
            customSHACL.clearNsPrefixMap();
            customSHACL.setNsPrefixes(insertModel);
            customSHACL.add(insertModel);
            ctx.commit("Update property SHACL");
        }
    }

    /**
     * Get all shacl shapes related to a class as a model.
     *
     * @param ontologyModel the ontology model
     * @param customSHACL the model containing the custom SHACL shapes
     * @param classUUID the class uuid
     * @return a model containing all SHACL shapes related to the class
     */
    private Model getClassShaclModel(Model ontologyModel, Model customSHACL, UUID classUUID) {
        var classUri =
                ontologyModel
                        .listSubjectsWithProperty(
                                RDFA.uuid, ontologyModel.createLiteral(classUUID.toString()))
                        .next()
                        .getURI();
        var nodeShapes = new SHACLShapesFetcher(customSHACL).getNodeShapesOfClass(classUri);
        var propertyShapeWrappers =
                new PropertyShapeToClassAssigner(customSHACL, ontologyModel)
                        .getPropertyShapes(classUUID);
        // get all shape uris to remove
        var shapesToRemove = new ArrayList<String>();
        for (var nodeShape : nodeShapes) {
            shapesToRemove.add(nodeShape.getId());
        }
        for (var propertyShapeWrapper : propertyShapeWrappers) {
            for (var propertyShape : propertyShapeWrapper.getPropertyShapes()) {
                shapesToRemove.add(propertyShape.getId());
            }
        }
        Model deleteModel = ModelFactory.createDefaultModel();
        for (var shapeToRemove : shapesToRemove) {
            copySHACLShapeToNewModel(
                    customSHACL, deleteModel, ResourceFactory.createResource(shapeToRemove));
        }
        return deleteModel;
    }

    /**
     * Copies the SHACL shape and its constraints to a new model.
     *
     * @param originalModel the original model containing the SHACL shapes
     * @param newModel the new model to copy the SHACL shapes to
     * @param subject the subject/uri of the SHACL shape
     */
    private void copySHACLShapeToNewModel(Model originalModel, Model newModel, Resource subject) {
        var stmtIterator = originalModel.listStatements(subject, null, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            var stmt = stmtIterator.nextStatement();
            newModel.add(stmt);
            var object = stmt.getObject();
            if (object.isAnon() || stmt.getPredicate().toString().equals(SHACL.sparql.getURI())) {
                copySHACLShapeToNewModel(originalModel, newModel, object.asResource());
            }
        }
    }
}
