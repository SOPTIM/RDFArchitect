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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.TxnType;
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
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.rdfarchitect.rdf.merge.ModelResourceExclusiveMerge;
import org.rdfarchitect.shacl.PropertyShapeToClassAssigner;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.SHACLShapesFetcher;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;

/**
 * This implementation is able to store a single shacl file. This is a temporary solution missing
 * the core concept of storing multiple shacl files.
 */
@RequiredArgsConstructor
public class SingletonPrimitiveSHACLStoringService
        implements SHACLInsertUseCase,
                SHACLExportUseCase,
                SHACLGetClassRelationsUseCase,
                SHACLGetShapeUseCase,
                SHACLReplaceShapeUseCase,
                SHACLDeleteShapeUseCase,
                SHACLUpdateUseCase {

    public static final PrefixEntry SHACL_NAMESPACE =
            PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL);
    private static Model customSHACLFile = ModelFactory.createDefaultModel();

    private final DatabasePort databasePort;

    @Override
    public void replaceCustomSHACLGraph(GraphIdentifier graphIdentifier, Graph shacl) {
        customSHACLFile = ModelFactory.createModelForGraph(shacl);
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var outStream = new ByteArrayOutputStream()) {
            customSHACLFile.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing SHACL graph to output stream", e);
        }
    }

    @Override
    public ByteArrayOutputStream exportGeneratedSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        GraphRewindable ontologyGraph = null;
        try (var outStream = new ByteArrayOutputStream()) {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();

            generatedShacl.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing SHACL graph to output stream", e);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
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
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();

            var mergedModel =
                    new ModelResourceExclusiveMerge().merge(customSHACLFile, generatedShacl);
            try (var outStream = new ByteArrayOutputStream()) {
                mergedModel.write(outStream, format.getLang().getName());
                return outStream;
            } catch (IOException e) {
                throw new DataAccessException(
                        "Error while writing combined shacl graph to output stream", e);
            }
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLPrefixes(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var outStream = new ByteArrayOutputStream()) {
            var prefixModel = ModelFactory.createDefaultModel();
            prefixModel.setNsPrefixes(customSHACLFile.getNsPrefixMap());
            prefixModel.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing SHACL prefixes to output stream", e);
        }
    }

    @Override
    public ByteArrayOutputStream exportGeneratedSHACLPrefixes(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var outStream = new ByteArrayOutputStream()) {
            var prefixModel = ModelFactory.createDefaultModel();
            prefixModel.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            prefixModel.setNsPrefix(SHACL_NAMESPACE.getPrefix(), SHACL_NAMESPACE.getUri());
            prefixModel.write(outStream, format.getLang().getName());
            return outStream;
        } catch (IOException e) {
            throw new DataAccessException("Error while writing SHACL prefixes to output stream", e);
        }
    }

    @Override
    public CustomAndGeneratedTuple<SHACLToClassRelations> getSHACLToClassRelations(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedSHACL =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(classUUID);
            var shaclResult = new CustomAndGeneratedTuple<SHACLToClassRelations>();
            shaclResult.setCustom(
                    getSHACLToClassRelations(ontologyModel, customSHACLFile, classUUID));
            shaclResult.setGenerated(
                    getSHACLToClassRelations(ontologyModel, generatedSHACL, classUUID));
            return shaclResult;
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
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
                .prefixes(prefixMappingToTtlString(prefixMapping))
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
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
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
            var customPropertyShapes =
                    new SHACLShapesFetcher(customSHACLFile)
                            .getPropertyShapesOfProperty(ontologyModel, property.getURI());
            var generatedPropertyShapes =
                    new SHACLShapesFetcher(generatedShacl)
                            .getPropertyShapesOfProperty(ontologyModel, property.getURI());
            return new CustomAndGeneratedTuple<List<PropertyShape>>()
                    .setCustom(customPropertyShapes)
                    .setGenerated(generatedPropertyShapes);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }

    @Override
    public CustomAndGeneratedTuple<List<NodeShape>> getNodeShapesForClass(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
            var classUri =
                    ontologyModel
                            .listSubjectsWithProperty(
                                    RDFA.uuid, ontologyModel.createLiteral(classUUID.toString()))
                            .next()
                            .getURI();
            var customNodeShapes =
                    new SHACLShapesFetcher(customSHACLFile).getNodeShapesOfClass(classUri);
            var generatedShacl =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(classUUID);
            var generatedNodeShapes =
                    new SHACLShapesFetcher(generatedShacl).getNodeShapesOfClass(classUri);
            return new CustomAndGeneratedTuple<List<NodeShape>>()
                    .setCustom(customNodeShapes)
                    .setGenerated(generatedNodeShapes);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }

    @Override
    public List<PropertyShapesWrapper> getPropertyShapes(
            GraphIdentifier graphIdentifier, UUID classUUID) {
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var shaclToClassAssigner =
                    new PropertyShapeToClassAssigner(
                            customSHACLFile, ModelFactory.createModelForGraph(ontologyGraph));
            return shaclToClassAssigner.getPropertyShapes(classUUID);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }

    @Override
    public void deleteSHACLShape(GraphIdentifier graphIdentifier, String shaclShapeURI) {
        var deleteModel = ModelFactory.createDefaultModel();
        copySHACLShapeToNewModel(
                customSHACLFile, deleteModel, ResourceFactory.createResource(shaclShapeURI));
        customSHACLFile.remove(deleteModel);
    }

    @Override
    public void replaceSHACLShape(
            GraphIdentifier graphIdentifier, String shaclShapeURI, String shaclToInsert) {
        Model insertModel = parseTriplesToModel(shaclToInsert);
        Model deleteModel = ModelFactory.createDefaultModel();
        copySHACLShapeToNewModel(
                customSHACLFile, deleteModel, ResourceFactory.createResource(shaclShapeURI));

        customSHACLFile.remove(deleteModel);
        customSHACLFile.add(insertModel);
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
        GraphRewindable ontologyGraph = null;
        try {
            ontologyGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);

            var insertModel = parseTriplesToModel(ttlShaclString);
            var deleteModel = getClassShaclModel(ontologyModel, classUUID);

            customSHACLFile.remove(deleteModel);
            customSHACLFile.clearNsPrefixMap();
            customSHACLFile.setNsPrefixes(insertModel);
            customSHACLFile.add(insertModel);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }

    @Override
    public void updatePropertyShacl(
            GraphIdentifier graphIdentifier, UUID propertyUUID, String ttlShaclString) {
        var insertModel = parseTriplesToModel(ttlShaclString);
        var propertyShapesOfProperty = getSHACLShapesByProperty(graphIdentifier, propertyUUID);
        var deleteModel = ModelFactory.createDefaultModel();
        for (var propertyShape : propertyShapesOfProperty.getCustom()) {
            copySHACLShapeToNewModel(
                    customSHACLFile,
                    deleteModel,
                    ResourceFactory.createResource(propertyShape.getId()));
        }

        customSHACLFile.remove(deleteModel);
        customSHACLFile.clearNsPrefixMap();
        customSHACLFile.setNsPrefixes(insertModel);
        customSHACLFile.add(insertModel);
    }

    /**
     * Get all shacl shapes related to a class as a model.
     *
     * @param ontologyModel the ontology model
     * @param classUUID the class uuid
     * @return a model containing all SHACL shapes related to the class
     */
    private Model getClassShaclModel(Model ontologyModel, UUID classUUID) {
        var classUri =
                ontologyModel
                        .listSubjectsWithProperty(
                                RDFA.uuid, ontologyModel.createLiteral(classUUID.toString()))
                        .next()
                        .getURI();
        var nodeShapes = new SHACLShapesFetcher(customSHACLFile).getNodeShapesOfClass(classUri);
        var propertyShapeWrappers =
                new PropertyShapeToClassAssigner(customSHACLFile, ontologyModel)
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
                    customSHACLFile, deleteModel, ResourceFactory.createResource(shapeToRemove));
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
