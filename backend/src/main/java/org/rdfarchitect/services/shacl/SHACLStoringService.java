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
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.merge.ModelResourceExclusiveMerge;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;
import org.rdfarchitect.services.shacl.form.ShapeBlockLocator;
import org.rdfarchitect.shacl.PropertyShapeToClassAssigner;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.SHACLShapesFetcher;
import org.rdfarchitect.shacl.dto.CustomAndGeneratedTuple;
import org.rdfarchitect.shacl.dto.NodeShape;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;
import org.rdfarchitect.shacl.dto.ShapeOrigin;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stores and reads the sets of SHACL shapes belonging to a graph.
 *
 * <p>Reads span every enabled {@link ShapesDocument} (see {@code readEnabledShapes}); the
 * shape-level and property-level writes still target the graph's default document, which is what
 * the endpoints predating multiple documents address.
 *
 * <p>That asymmetry is why {@link #updateClassSHACL} and {@link #updatePropertyShacl} are
 * deprecated and no longer reached from the UI. Editing a rule that came from an imported document
 * could not remove it — it is not in the default document — so the edit was <em>added</em> beside
 * the original, and SHACL being conjunctive the two then contradicted each other. Neither method
 * updates the default document's {@code rawText} either, so the workbench kept showing the text
 * from before the edit and overwrote it on the next save. Write through {@link
 * #replaceShapesDocumentText}, which addresses one document and keeps its text authoritative.
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

    private final DatabasePort databasePort;

    // -------------------------------------------------------------------------
    // Shapes documents
    // -------------------------------------------------------------------------

    @Override
    public List<ShapesDocumentInfo> listShapesDocuments(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ctx.getShapesDocuments().values().stream()
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
        var parsed = parse(content, lang);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            assertNameIsFree(ctx, name, null);
            var document = ctx.createShapesDocument(name, resolveOrigin(sourceFileName));
            document.setSourceFileName(sourceFileName);
            writeContent(document, parsed, content, lang);
            ctx.commit("Add constraints \"%s\"".formatted(name));
            return toInfo(document);
        }
    }

    @Override
    public String getShapesDocumentText(GraphIdentifier graphIdentifier, UUID documentId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var document = requireDocument(ctx, documentId);
            // Authoritative text, unless the document has none yet (a snapshot carries only the
            // triples, and an undo drops the text) — then it is re-derived from the shapes.
            return document.getRawText() != null
                    ? document.getRawText()
                    : serialiseToTurtle(ModelFactory.createModelForGraph(document.getGraph()));
        }
    }

    @Override
    public void replaceShapesDocumentText(
            GraphIdentifier graphIdentifier, UUID documentId, String turtle) {
        var parsed = parse(turtle, Lang.TURTLE);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var document = requireDocument(ctx, documentId);
            writeContent(document, parsed, turtle, Lang.TURTLE);
            ctx.commit("Edit constraints \"%s\"".formatted(document.getName()));
        }
    }

    @Override
    public ShapesDocumentInfo updateShapesDocument(
            GraphIdentifier graphIdentifier,
            UUID documentId,
            String name,
            Boolean enabled,
            Integer order) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var document = requireDocument(ctx, documentId);
            if (name != null) {
                assertNameIsFree(ctx, name, documentId);
                document.setName(name);
            }
            if (enabled != null) {
                document.setEnabled(enabled);
            }
            if (order != null) {
                reorder(ctx, document, order);
            }
            ctx.commit("Update constraints \"%s\"".formatted(document.getName()));
            return toInfo(document);
        }
    }

    @Override
    public void deleteShapesDocument(GraphIdentifier graphIdentifier, UUID documentId) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var document = requireDocument(ctx, documentId);
            if (GraphContext.DEFAULT_SHAPES_DOCUMENT_ID.equals(documentId)) {
                // Refusing here rather than letting the context's guard surface as a server error:
                // asking to delete it is a client mistake, not a fault.
                throw new ResourceConflictException(
                        "The graph's default constraints document cannot be deleted. "
                                + "Replace its content with an empty document instead.");
            }
            var name = document.getName();
            ctx.removeShapesDocument(documentId);
            ctx.commit("Delete constraints \"%s\"".formatted(name));
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

    /** Names identify documents to the user, so two documents must not share one. */
    private static void assertNameIsFree(GraphContext ctx, String name, UUID allowedId) {
        var clash =
                ctx.getShapesDocuments().values().stream()
                        .anyMatch(d -> d.getName().equals(name) && !d.getId().equals(allowedId));
        if (clash) {
            throw new ResourceConflictException(
                    "A constraints document named \"" + name + "\" already exists in this graph.");
        }
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
                        ? content
                        : serialiseToTurtle(storedModel));
    }

    /**
     * Parses content the client sent, reporting a syntax error as one.
     *
     * <p>Jena's own message carries the line and column, which is the only part of the answer the
     * user can do anything with, so it is passed through rather than replaced.
     */
    private static Model parse(String content, Lang lang) {
        var model = ModelFactory.createDefaultModel();
        try (var reader = new StringReader(content == null ? "" : content.trim())) {
            model.read(reader, null, lang.getName());
        } catch (RiotException e) {
            throw new InvalidContentException(
                    "The constraints could not be read as %s: %s"
                            .formatted(lang.getLabel(), e.getMessage()));
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
        return ShapesDocumentInfo.builder()
                .id(document.getId())
                .name(document.getName())
                .sourceFileName(document.getSourceFileName())
                .origin(document.getOrigin())
                .enabled(document.isEnabled())
                .order(document.getOrder())
                .isDefault(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID.equals(document.getId()))
                .tripleCount(document.getGraph().size())
                .build();
    }

    /**
     * All enabled shapes documents of the graph, read as one model.
     *
     * <p>SHACL is conjunctive: shapes targeting the same focus node all apply, and the language has
     * no notion of one shape overriding another. Documents are therefore unioned, never resolved
     * against each other — a precedence rule here would disagree with the file the user exports and
     * with every SHACL engine that validates it. Where two documents genuinely cannot both hold (an
     * unsatisfiable pair, or the same shape IRI defined twice), that is reported as a validation
     * finding rather than silently decided.
     *
     * <p>Documents are added in {@link ShapesDocument#getOrder()} order so serialisation is stable.
     * Disabled documents are left out: switching a document off is how a user takes its constraints
     * out of validation and export.
     *
     * <p>Must be called inside a transaction on {@code ctx}. The result is detached from the stored
     * graphs, so writes to it are not persisted.
     */
    @Override
    public ByteArrayOutputStream exportSelectedSHACLGraph(
            GraphIdentifier graphIdentifier,
            RDFFormat format,
            Collection<UUID> documentIds,
            boolean includeGenerated) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var selected = readShapesOf(ctx, documentIds);
            var model = selected;
            if (includeGenerated) {
                var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
                ontologyModel.setNsPrefixes(
                        databasePort.getPrefixMapping(graphIdentifier.datasetName()));
                var generated =
                        new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true).generate();
                // Custom shapes win over generated ones for the same resource: the generated ones
                // are derived defaults, and this is the same rule the combined export follows.
                model = new ModelResourceExclusiveMerge().merge(selected, generated);
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

    /**
     * The named documents merged, in the graph's own order.
     *
     * <p>Enabled state is ignored on purpose — see {@code exportSelectedSHACLGraph}. An id that
     * names no document is skipped rather than refused, so a stale selection still exports what
     * remains.
     */
    private static Model readShapesOf(GraphContext ctx, Collection<UUID> documentIds) {
        var wanted = Set.copyOf(documentIds == null ? List.<UUID>of() : documentIds);
        var union = ModelFactory.createDefaultModel();
        ctx.getShapesDocuments().values().stream()
                .filter(document -> wanted.contains(document.getId()))
                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                .forEach(document -> addWithPrefixes(union, document));
        return union;
    }

    private static Model readEnabledShapes(GraphContext ctx) {
        var union = ModelFactory.createDefaultModel();
        ctx.getShapesDocuments().values().stream()
                .filter(ShapesDocument::isEnabled)
                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                .forEach(document -> addWithPrefixes(union, document));
        return union;
    }

    /** Adds a document's triples, letting the first document to bind a prefix keep it. */
    private static void addWithPrefixes(Model union, ShapesDocument document) {
        var model = ModelFactory.createModelForGraph(document.getGraph());
        // The first document to declare a prefix keeps it, so a later document rebinding it
        // cannot change what earlier shapes mean.
        model.getNsPrefixMap()
                .forEach(
                        (prefix, uri) -> {
                            if (union.getNsPrefixURI(prefix) == null) {
                                union.setNsPrefix(prefix, uri);
                            }
                        });
        union.add(model);
    }

    @Override
    public void replaceCustomSHACLGraph(GraphIdentifier graphIdentifier, Graph shacl) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var document = ctx.getShapesDocuments().get(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID);
            var newModel = ModelFactory.createModelForGraph(GraphUtils.normalizeBlankNodes(shacl));
            // This path receives an already-parsed graph, so there is no user-authored text to
            // preserve; the serialised form is the best available source for the editor.
            writeContent(document, newModel, null, null);

            ctx.commit("Replace custom SHACL");
        }
    }

    @Override
    public ByteArrayOutputStream exportCustomSHACLGraph(
            GraphIdentifier graphIdentifier, RDFFormat format) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var customSHACL = readEnabledShapes(ctx);
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
            var customSHACL = readEnabledShapes(ctx);
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
            var customSHACL = readEnabledShapes(ctx);
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
            var customSHACL = readEnabledShapes(ctx);
            var ontologyModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            ontologyModel.setNsPrefixes(
                    databasePort.getPrefixMapping(graphIdentifier.datasetName()));
            var generatedSHACL =
                    new SHACLFromCIMGenerator(ontologyModel, SHACL_NAMESPACE, true)
                            .generateForClassOnly(classUUID);
            var shaclResult = new CustomAndGeneratedTuple<SHACLToClassRelations>();
            var custom = getSHACLToClassRelations(ontologyModel, customSHACL, classUUID);
            attributeToDocuments(custom, ctx);
            shaclResult.setCustom(custom);
            shaclResult.setGenerated(
                    getSHACLToClassRelations(ontologyModel, generatedSHACL, classUUID));
            return shaclResult;
        }
    }

    // -------------------------------------------------------------------------
    // Where a shape came from, and what it asks for
    // -------------------------------------------------------------------------

    /**
     * Names the documents behind every shape of a merged result.
     *
     * <p>The shapes were read from the union of the enabled documents, which is the only way to
     * answer "what constrains this class" — official constraints are split across files. Merging is
     * also what throws away the one thing a reader needs in order to change a rule, so it is put
     * back here.
     *
     * <p>Shapes are matched by the id the fetchers already produce, which is {@code
     * RDFNode.toString()} — the IRI for a named shape, the label for a blank node. Blank node
     * identity survives the merge, because adding a model copies its triples rather than rewriting
     * them, so an inlined property shape is attributed as reliably as a named one.
     */
    private static void attributeToDocuments(SHACLToClassRelations relations, GraphContext ctx) {
        var byId = new LinkedHashMap<String, List<ShapeOrigin>>();
        ctx.getShapesDocuments().values().stream()
                .filter(ShapesDocument::isEnabled)
                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                .forEach(document -> indexSubjects(byId, document));

        for (NodeShape shape : orEmpty(relations.getNodeShapes())) {
            shape.setOrigins(byId.getOrDefault(shape.getId(), List.of()));
        }
        for (PropertyShapesWrapper wrapper :
                concat(relations.getPropertyShapes(), relations.getDerivedPropertyShapes())) {
            for (PropertyShape shape : orEmpty(wrapper.getPropertyShapes())) {
                shape.setOrigins(byId.getOrDefault(shape.getId(), List.of()));
            }
        }
    }

    /** Records every subject a document states something about, under its shape id. */
    private static void indexSubjects(
            Map<String, List<ShapeOrigin>> byId, ShapesDocument document) {
        var model = ModelFactory.createModelForGraph(document.getGraph());
        var text = document.getRawText();
        var seen = new HashSet<String>();
        model.listSubjects()
                .forEachRemaining(
                        subject -> {
                            var id = subject.toString();
                            if (!seen.add(id)) {
                                return;
                            }
                            byId.computeIfAbsent(id, ignored -> new ArrayList<>())
                                    .add(
                                            ShapeOrigin.builder()
                                                    .documentId(document.getId())
                                                    .documentName(document.getName())
                                                    .line(lineOf(text, subject, model))
                                                    .build());
                        });
    }

    /**
     * The 1-based line a shape's statement starts on, or {@code null} when it cannot be found.
     *
     * <p>Counted in the document's own text rather than in a re-serialisation, because the text is
     * what the workbench will open and the line has to agree with it. A blank-node shape has no
     * subject to search for, and a document restored from a snapshot has no text at all.
     */
    private static Integer lineOf(String turtle, Resource subject, Model model) {
        if (turtle == null || !subject.isURIResource()) {
            return null;
        }
        return ShapeBlockLocator.locate(turtle, subject.getURI(), model)
                .map(statement -> (int) turtle.substring(0, statement.start()).lines().count() + 1)
                .orElse(null);
    }

    private static List<PropertyShapesWrapper> concat(
            List<PropertyShapesWrapper> first, List<PropertyShapesWrapper> second) {
        var all = new ArrayList<>(orEmpty(first));
        all.addAll(orEmpty(second));
        return all;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
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
        var relations =
                SHACLToClassRelations.builder()
                        .namespaces(prefixMappingToTtlString(prefixMapping))
                        .nodeShapes(shaclShapesFetcher.getNodeShapesOfClass(classUri))
                        .propertyShapes(shaclToClassAssigner.getPropertyShapes(classUUID))
                        .derivedPropertyShapes(
                                shaclToClassAssigner.getDerivedPropertyShapesOfClass(classUUID))
                        .build();
        summarise(relations, shaclModel, prefixMapping);
        return relations;
    }

    /**
     * Puts the effective rule of each property into words, so it can be read without expanding the
     * Turtle underneath it.
     *
     * <p>Shapes are looked up by the id the fetcher recorded, which is {@code RDFNode.toString()};
     * building the index once and matching on it exactly avoids having to guess how a blank node
     * spells itself.
     */
    private static void summarise(
            SHACLToClassRelations relations, Model shaclModel, PrefixMapping prefixes) {
        var subjects = new HashMap<String, Node>();
        shaclModel
                .listSubjects()
                .forEachRemaining(subject -> subjects.put(subject.toString(), subject.asNode()));

        for (PropertyShapesWrapper wrapper :
                concat(relations.getPropertyShapes(), relations.getDerivedPropertyShapes())) {
            var shapes =
                    orEmpty(wrapper.getPropertyShapes()).stream()
                            .map(shape -> subjects.get(shape.getId()))
                            .filter(Objects::nonNull)
                            .toList();
            wrapper.setSummary(
                    EffectiveConstraints.describe(
                            EffectiveConstraints.readAll(shaclModel.getGraph(), shapes), prefixes));
        }
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
            var customSHACL = readEnabledShapes(ctx);
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
            var customSHACL = readEnabledShapes(ctx);
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
            var customSHACL = readEnabledShapes(ctx);
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
