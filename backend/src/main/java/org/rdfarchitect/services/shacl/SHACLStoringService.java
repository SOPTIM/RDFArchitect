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
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.exception.database.StaleWriteException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.merge.ModelResourceExclusiveMerge;
import org.rdfarchitect.services.ChangeDescriptions;
import org.rdfarchitect.shacl.PropertyShapeToClassAssigner;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.SHACLShapesFetcher;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores and reads the SHACL shapes belonging to a graph, of which there are two separate stores:
 * the single custom SHACL graph, and any number of {@link ShapesDocument shapes documents}. Nothing
 * here reads one on behalf of the other.
 */
@RequiredArgsConstructor
public class SHACLStoringService
        implements SHACLInsertUseCase,
                SHACLExportUseCase,
                SHACLGetClassRelationsUseCase,
                SHACLGetShapeUseCase,
                SHACLReplaceShapeUseCase,
                SHACLDeleteShapeUseCase,
                SHACLUpdateUseCase,
                SHACLDocumentUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SHACLStoringService.class);

    public static final PrefixEntry SHACL_NAMESPACE =
            PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL);

    /** Stem of the name a document gets when it is created without one. */
    private static final String DEFAULT_DOCUMENT_NAME = "constraints";

    private final DatabasePort databasePort;

    @Override
    public void replaceCustomSHACLGraph(GraphIdentifier graphIdentifier, Graph shacl) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var storedGraph = ctx.getCustomSHACL();
            storedGraph.clear();
            var storedModel = ModelFactory.createModelForGraph(storedGraph);
            var newModel = ModelFactory.createModelForGraph(GraphUtils.normalizeBlankNodes(shacl));
            storedModel.clearNsPrefixMap();
            storedModel.add(newModel);
            storedModel.setNsPrefixes(newModel);

            transaction.commit("Replaced the custom SHACL");
        }
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                        databasePort.beginTransaction(
                                graphIdentifier.datasetName(), ReadWrite.READ);
                var outStream = new ByteArrayOutputStream()) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var shaclToClassAssigner =
                    new PropertyShapeToClassAssigner(
                            customSHACL, ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            return shaclToClassAssigner.getPropertyShapes(classUUID);
        }
    }

    @Override
    public void deleteSHACLShape(GraphIdentifier graphIdentifier, String shaclShapeURI) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var deleteModel = ModelFactory.createDefaultModel();
            copySHACLShapeToNewModel(
                    customSHACL, deleteModel, ResourceFactory.createResource(shaclShapeURI));
            customSHACL.remove(deleteModel);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Deleted", "SHACL shape", ChangeDescriptions.localName(shaclShapeURI)));
        }
    }

    @Override
    public void replaceSHACLShape(
            GraphIdentifier graphIdentifier, String shaclShapeURI, String shaclToInsert) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            Model insertModel = parseTriplesToModel(shaclToInsert);
            Model deleteModel = ModelFactory.createDefaultModel();
            copySHACLShapeToNewModel(
                    customSHACL, deleteModel, ResourceFactory.createResource(shaclShapeURI));
            customSHACL.remove(deleteModel);
            customSHACL.add(insertModel);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Replaced",
                            "SHACL shape",
                            ChangeDescriptions.localName(shaclShapeURI)));
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var customSHACL = ModelFactory.createModelForGraph(ctx.getCustomSHACL());
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            var insertModel = parseTriplesToModel(ttlShaclString);
            var deleteModel = getClassShaclModel(ontologyModel, customSHACL, classUUID);

            customSHACL.remove(deleteModel);
            customSHACL.clearNsPrefixMap();
            customSHACL.setNsPrefixes(insertModel);
            customSHACL.add(insertModel);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Updated the SHACL of",
                            "class",
                            ChangeDescriptions.className(ctx.getRdfGraph(), classUUID)));
        }
    }

    @Override
    public void updatePropertyShacl(
            GraphIdentifier graphIdentifier, UUID propertyUUID, String ttlShaclString) {
        var insertModel = parseTriplesToModel(ttlShaclString);

        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
            transaction.commit("Updated the SHACL of a property");
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

    // -------------------------------------------------------------------------
    // Shapes documents
    // -------------------------------------------------------------------------

    @Override
    public List<ShapesDocumentInfo> listShapesDocuments(GraphIdentifier graphIdentifier) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            return transaction
                    .graph(graphIdentifier.graphUri())
                    .getShapesDocuments()
                    .values()
                    .stream()
                    .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                    .map(SHACLStoringService::toInfo)
                    .toList();
        }
    }

    @Override
    public ShapesDocumentInfo createShapesDocument(
            GraphIdentifier graphIdentifier,
            String name,
            String sourceFileName,
            String content,
            Lang lang) {
        assertEditable(graphIdentifier);
        var requestedName = name == null ? null : validName(name);
        var parsed = parse(content, lang);
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var documentName = requestedName != null ? requestedName : freeDefaultName(ctx);
            assertNameIsFree(ctx, documentName, null);
            // A failure before the commit needs no cleanup: the new document is part of the
            // transaction, and leaving the block without committing discards it.
            var document = ctx.createShapesDocument(documentName, resolveOrigin(sourceFileName));
            document.setSourceFileName(sourceFileName);
            writeContent(document, parsed, content, lang);
            transaction.commit(
                    ChangeDescriptions.actionOn("Added", "constraints document", documentName));
            return toInfo(document);
        }
    }

    @Override
    public String getShapesDocumentText(GraphIdentifier graphIdentifier, UUID documentId) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            return textOf(
                    requireDocument(transaction.graph(graphIdentifier.graphUri()), documentId));
        }
    }

    /** Authoritative text, re-derived from the shapes for a document that has none. */
    private static String textOf(ShapesDocument document) {
        return document.getRawText() != null
                ? document.getRawText()
                : serialiseToTurtle(ModelFactory.createModelForGraph(document.getGraph()));
    }

    @Override
    public void replaceShapesDocumentText(
            GraphIdentifier graphIdentifier,
            UUID documentId,
            String turtle,
            String expectedRevision) {
        assertEditable(graphIdentifier);
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var document =
                    requireDocument(transaction.graph(graphIdentifier.graphUri()), documentId);
            if (expectedRevision != null
                    && !expectedRevision.equalsIgnoreCase(
                            SHACLDocumentUseCase.revisionOf(textOf(document)))) {
                throw new StaleWriteException(
                        "\"%s\" has changed since it was opened. Saving would discard that change."
                                .formatted(document.getName()));
            }
            // Saving what is already stored changes nothing; not even parsing it is needed.
            if (storedTurtle(turtle).equals(document.getRawText())) {
                return;
            }
            writeContent(document, parse(turtle, Lang.TURTLE), turtle, Lang.TURTLE);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Edited", "constraints document", document.getName()));
        }
    }

    @Override
    public ShapesDocumentInfo updateShapesDocument(
            GraphIdentifier graphIdentifier,
            UUID documentId,
            String name,
            Boolean enabled,
            Integer order) {
        assertEditable(graphIdentifier);
        var newName = name == null ? null : validName(name);
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var document = requireDocument(ctx, documentId);
            if (newName != null) {
                assertNameIsFree(ctx, newName, documentId);
                document.setName(newName);
            }
            if (enabled != null) {
                document.setEnabled(enabled);
            }
            if (order != null) {
                reorder(ctx, document, order);
            }
            // An update that changes nothing records nothing: only what changed gains a version.
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Updated", "constraints document", document.getName()));
            return toInfo(document);
        }
    }

    @Override
    public void deleteShapesDocument(GraphIdentifier graphIdentifier, UUID documentId) {
        assertEditable(graphIdentifier);
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var document = requireDocument(ctx, documentId);
            ctx.removeShapesDocument(documentId);
            transaction.commit(
                    ChangeDescriptions.actionOn(
                            "Deleted", "constraints document", document.getName()));
        }
    }

    /**
     * Exports the named documents as one file, optionally merged with the generated shapes.
     *
     * <p>Enabled state is ignored: a document is included because the user ticked it. Switching one
     * off takes it out of validation, not out of the user's reach.
     *
     * <p>A single document asked for as Turtle, on its own, is handed back as the text it holds —
     * an imported file comes back byte for byte, comments and ordering included, instead of as a
     * re-serialisation of its triples. That holds for one holding only comments as well. Beside
     * others, a document with no triples adds nothing to a merge, so it does not stop the one that
     * does from coming back as written.
     */
    @Override
    public ByteArrayOutputStream exportSelectedSHACLGraph(
            GraphIdentifier graphIdentifier,
            RDFFormat format,
            Collection<UUID> documentIds,
            boolean includeGenerated) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var ids = Set.copyOf(documentIds == null ? List.<UUID>of() : documentIds);
            var requested = ids.stream().map(id -> requireDocument(ctx, id)).toList();
            var contributing =
                    requested.size() == 1
                            ? requested
                            : requested.stream()
                                    .filter(document -> !document.getGraph().isEmpty())
                                    .toList();
            if (contributing.size() == 1
                    && !includeGenerated
                    && Lang.TURTLE.equals(format.getLang())) {
                var rawText = contributing.getFirst().getRawText();
                if (rawText != null) {
                    var out = new ByteArrayOutputStream();
                    out.writeBytes(rawText.getBytes(StandardCharsets.UTF_8));
                    return out;
                }
            }
            var model = readShapesOf(ctx, ids);
            if (includeGenerated) {
                var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
                ontologyModel.setNsPrefixes(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()));
                var generated =
                        new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();
                // Documents win over generated shapes for the same resource: the generated ones
                // are derived defaults, and this is the same rule the combined export follows.
                model = new ModelResourceExclusiveMerge().merge(model, generated);
            }
            try (var outStream = new ByteArrayOutputStream()) {
                model.write(outStream, format.getLang().getName());
                return outStream;
            } catch (IOException e) {
                throw new DataAccessException(
                        "Error while writing the selected shapes to output stream", e);
            }
        }
    }

    /** The named documents merged in the graph's own order, whether enabled or not. */
    private static Model readShapesOf(GraphContext ctx, Set<UUID> documentIds) {
        var union = ModelFactory.createDefaultModel();
        ctx.getShapesDocuments().values().stream()
                .filter(document -> documentIds.contains(document.getId()))
                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                .forEach(document -> addWithPrefixes(union, document));
        return union;
    }

    /**
     * Adds a document's triples. The first document to declare a prefix keeps it, so a later
     * document rebinding it cannot change what earlier shapes mean.
     *
     * <p>Blank nodes stay apart per document: a label only identifies a node within the document it
     * was normalised in, and merging on it would fold together two lists that merely look alike.
     */
    private static void addWithPrefixes(Model union, ShapesDocument document) {
        var model = ModelFactory.createModelForGraph(document.getGraph());
        model.getNsPrefixMap()
                .forEach(
                        (prefix, uri) -> {
                            if (union.getNsPrefixURI(prefix) == null) {
                                union.setNsPrefix(prefix, uri);
                            }
                        });
        var scope = document.getId() + ":";
        document.getGraph()
                .find()
                .forEach(
                        triple ->
                                union.getGraph()
                                        .add(
                                                Triple.create(
                                                        scoped(triple.getSubject(), scope),
                                                        triple.getPredicate(),
                                                        scoped(triple.getObject(), scope))));
    }

    private static Node scoped(Node node, String scope) {
        return node.isBlank()
                ? NodeFactory.createBlankNode(scope + node.getBlankNodeLabel())
                : node;
    }

    /**
     * Refuses a write to a read-only workspace.
     *
     * <p>A client checks the flag too, but anything else talking to these endpoints — a second tab
     * that has not noticed the switch, a script — would otherwise change a workspace the user has
     * frozen.
     */
    private void assertEditable(GraphIdentifier graphIdentifier) {
        if (databasePort.isReadOnly(graphIdentifier.datasetName())) {
            throw new ResourceConflictException(
                    "The workspace \"%s\" is read-only. Enable editing to change its constraints."
                            .formatted(graphIdentifier.datasetName()));
        }
    }

    private static ShapesDocument requireDocument(GraphContext ctx, UUID documentId) {
        var document = ctx.getShapesDocuments().get(documentId);
        if (document == null) {
            throw new ResourceNotFoundException(
                    "No constraints document with id " + documentId + " in this graph.");
        }
        return document;
    }

    /**
     * Names identify documents to the user, so two documents must not share one — nor differ only
     * in case, which reads as the same name and collides as a file name on export.
     */
    private static void assertNameIsFree(GraphContext ctx, String name, UUID allowedId) {
        var clash =
                ctx.getShapesDocuments().values().stream()
                        .anyMatch(
                                d ->
                                        d.getName().equalsIgnoreCase(name)
                                                && !d.getId().equals(allowedId));
        if (clash) {
            throw new ResourceConflictException(
                    "A constraints document named \"" + name + "\" already exists in this graph.");
        }
    }

    /** The name as it will be stored, or a client error when it cannot serve as one. */
    private static String validName(String name) {
        var trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidContentException("A constraints document needs a name.");
        }
        if (trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidContentException(
                    "A constraints document name cannot contain control characters.");
        }
        return trimmed;
    }

    /** For a document created without a name, such as an upload whose file name is unknown. */
    private static String freeDefaultName(GraphContext ctx) {
        var taken =
                ctx.getShapesDocuments().values().stream()
                        .map(document -> document.getName().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
        var candidate = DEFAULT_DOCUMENT_NAME + ".ttl";
        for (int i = 2; taken.contains(candidate); i++) {
            candidate = DEFAULT_DOCUMENT_NAME + "-" + i + ".ttl";
        }
        return candidate;
    }

    /**
     * Moves {@code document} to {@code targetOrder} and renumbers the rest so the positions stay
     * dense — a gap would make the next insert land in an unexpected place.
     */
    private static void reorder(GraphContext ctx, ShapesDocument document, int targetOrder) {
        var ordered =
                new ArrayList<>(
                        ctx.getShapesDocuments().values().stream()
                                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                                .toList());
        ordered.remove(document);
        var clamped = Math.clamp(targetOrder, 0, ordered.size());
        ordered.add(clamped, document);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setOrder(i);
        }
    }

    /** Uploaded content is treated as imported; content typed into the editor as authored. */
    private static ShapesDocument.Origin resolveOrigin(String sourceFileName) {
        return sourceFileName != null
                ? ShapesDocument.Origin.IMPORTED
                : ShapesDocument.Origin.AUTHORED;
    }

    /**
     * Replaces a document's shapes and records the text they came from.
     *
     * <p>Turtle is kept exactly as the user wrote it, comments and ordering included. Any other
     * syntax is converted to Turtle once, here, because the editor only works in Turtle — keeping
     * the original RDF/XML would mean handing the editor something it cannot show.
     */
    private static void writeContent(
            ShapesDocument document, Model parsed, String content, Lang lang) {
        var stored = document.getGraph();
        stored.clear();
        var storedModel = ModelFactory.createModelForGraph(stored);
        storedModel.clearNsPrefixMap();
        storedModel.add(parsed);
        storedModel.setNsPrefixes(parsed);
        document.setRawText(
                Lang.TURTLE.equals(lang) && content != null
                        ? storedTurtle(content)
                        : serialiseToTurtle(storedModel));
    }

    /**
     * Turtle as it is recorded.
     *
     * <p>A document emptied in an editor still has to be sent as something — Spring rejects an
     * absent plain-string body — so whitespace-only content is stored as the empty document it
     * means. Keeping the sent " " would make the text read back differ from the text sent.
     */
    private static String storedTurtle(String content) {
        return content == null || content.isBlank() ? "" : content;
    }

    /**
     * Parses content the client sent, reporting a syntax error as one.
     *
     * <p>Jena's own message carries the line and column, which is the only part of the answer the
     * user can do anything with, so it is passed through rather than replaced.
     */
    private static Model parse(String content, Lang lang) {
        var model = ModelFactory.createDefaultModel();
        // Only RDF/XML needs this, which allows nothing before its declaration. Trimming anything
        // else would move the line and column a syntax error reports.
        var text = content == null ? "" : content;
        try (var reader = new StringReader(Lang.RDFXML.equals(lang) ? text.trim() : text)) {
            model.read(reader, null, lang.getName());
        } catch (RiotException e) {
            throw new InvalidContentException(
                    "The constraints could not be read as %s: %s"
                            .formatted(lang.getLabel(), e.getMessage()));
        } catch (StackOverflowError e) {
            // Jena parses brackets recursively, so this says something about the text and not about
            // the server.
            throw new InvalidContentException(ShapesTurtleParser.TOO_DEEP);
        }
        return ModelFactory.createModelForGraph(GraphUtils.normalizeBlankNodes(model.getGraph()));
    }

    private static String serialiseToTurtle(Model model) {
        try (var out = new ByteArrayOutputStream()) {
            model.write(out, Lang.TURTLE.getName());
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Error while writing constraints as Turtle", e);
        }
    }

    private static ShapesDocumentInfo toInfo(ShapesDocument document) {
        var counts = ShapeCounts.of(document.getGraph());
        return ShapesDocumentInfo.builder()
                .id(document.getId())
                .name(document.getName())
                .sourceFileName(document.getSourceFileName())
                .origin(document.getOrigin())
                .enabled(document.isEnabled())
                .order(document.getOrder())
                .tripleCount(document.getGraph().size())
                .nodeShapeCount(counts.nodeShapes())
                .propertyShapeCount(counts.propertyShapes())
                .sparqlConstraintCount(counts.sparqlConstraints())
                .build();
    }
}
