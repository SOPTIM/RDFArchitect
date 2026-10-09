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

package org.rdfarchitect.services.shacl.conformance;

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.shacl.effective.ClassHierarchy;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;
import org.rdfarchitect.shacl.SHACLFromCIMGenerator;
import org.rdfarchitect.shacl.dto.ConformanceDocument;
import org.rdfarchitect.shacl.dto.ConformanceFinding;
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Answers "does my schema still agree with the constraints that came with it?".
 *
 * <p>Both sides are turned into the same thing before they are compared: one statement per class
 * and property, merged from however many shapes said it. That is what makes the comparison possible
 * at all — generated shapes and official ENTSO-E ones share no naming convention, and both sides
 * spread one property's rules over separate cardinality, datatype and value-type shapes.
 *
 * <p>The right-hand side is the graph's <em>enabled</em> documents together, not the one being
 * looked at. A graph's constraints are their conjunction, and official releases split their rules
 * across files on purpose — reading one alone reported its neighbours' coverage as missing. The
 * open document joins in even when it is disabled, because it is the one the question is about.
 *
 * <p>The left-hand side is the <em>workspace's</em> schema, scoped to this graph. Profiles build on
 * each other — an NC profile adds properties to classes the Equipment profile declares, and
 * inherits everything else from it — so generating from one graph alone left NC documents with
 * hundreds of constraints "the schema does not have", with Equipment loaded right next to it. What
 * the graph is asked to cover is still only its own properties; what the documents are compared
 * against is everything the workspace says about the classes they target. Where two graphs declare
 * the same property, the graph being compared speaks for it.
 *
 * <p>Inverse cardinality is left out. RDFArchitect states it as {@code sh:path [ sh:inversePath …
 * ]}, a path expression rather than a property, and there is nothing on the other side to line it
 * up with.
 */
public class ConformanceService implements ConformanceUseCase {

    /** Generated schemas to keep. A handful: one per graph someone is looking at. */
    private static final int MAX_CACHED = 8;

    private final DatabasePort databasePort;

    /**
     * What the schema implies, per workspace content.
     *
     * <p>Generating shapes takes seconds on a large profile (about ten for CGMES Dynamics), and the
     * report is asked for again every time the panel opens. The key holds every graph's version,
     * because the scope is the whole workspace; and the session, because the in-memory database is
     * per session.
     */
    private final Map<CacheKey, Implied> cache = new LeastRecentlyUsed<>(MAX_CACHED);

    /** Access-ordered, dropping the least recently used entry beyond {@code capacity}. */
    private static final class LeastRecentlyUsed<K, V> extends LinkedHashMap<K, V> {

        @java.io.Serial private static final long serialVersionUID = 1L;

        private final int capacity;

        LeastRecentlyUsed(int capacity) {
            super(16, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }

    public ConformanceService(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    private record CacheKey(
            String sessionId,
            String datasetName,
            String graphUri,
            Map<String, UUID> graphVersions,
            Set<String> targetedClasses) {}

    /**
     * @param constraints what the schema implies for the classes that matter to this graph
     * @param ownClasses the classes this graph declares
     * @param ownProperties the properties this graph declares; with {@code ownClasses}, what it is
     *     asked to cover
     * @param properties every property the workspace declares
     */
    private record Implied(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> constraints,
            ClassHierarchy hierarchy,
            Set<String> ownClasses,
            Set<String> ownProperties,
            Set<String> properties) {

        /**
         * Whether this graph is expected to state {@code key} itself. Profiles re-declare the
         * classes and properties they share — nearly every one carries {@code
         * IdentifiedObject.name} — so a property alone would hold each graph to account for every
         * class in the workspace.
         */
        boolean owns(EffectiveConstraints.Key key) {
            return ownClasses.contains(key.targetClass()) && ownProperties.contains(key.path());
        }
    }

    @Override
    public ConformanceReport compare(GraphIdentifier graphIdentifier, UUID documentId) {
        var prefixes = databasePort.getPrefixMapping(graphIdentifier.datasetName());

        var documentShapes = new LinkedHashMap<String, Graph>();
        var documentRefs = new ArrayList<ConformanceDocument>();
        String documentName;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var documents = ctx.getShapesDocuments();
            var opened = documents.get(documentId);
            if (opened == null) {
                throw new ResourceNotFoundException(
                        "No constraints document with id " + documentId + " in this graph.");
            }
            documentName = opened.getName();
            documents.values().stream()
                    .filter(document -> document.isEnabled() || document.getId().equals(documentId))
                    // Reading order, matching the document list and every other merge, so the
                    // report's "read together" line names them the way the workbench shows them.
                    .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                    .forEach(
                            document -> {
                                documentShapes.put(document.getName(), copyOf(document.getGraph()));
                                documentRefs.add(
                                        new ConformanceDocument(
                                                document.getId(), document.getName()));
                            });
        }

        var schema = impliedFor(graphIdentifier, targetedClasses(documentShapes.values()));
        var asserted =
                OtherVocabularies.leaveOut(
                        EffectiveConstraints.of(documentShapes),
                        schema.hierarchy(),
                        schema.properties());
        var stated = new HashSet<>(asserted.constraints().keySet());
        stated.addAll(asserted.advisory().keySet());

        // Everything the workspace implies for a stated key is compared; only this graph's own
        // properties are expected to be covered.
        var implied =
                new LinkedHashMap<EffectiveConstraints.Key, EffectiveConstraints.Constraint>();
        schema.constraints()
                .forEach(
                        (key, constraint) -> {
                            if (schema.owns(key) || stated.contains(key)) {
                                implied.put(key, constraint);
                            }
                        });

        var findings =
                ConformanceComparator.compare(
                        implied, asserted, prefixes, schema.hierarchy(), schema.properties());

        var contradicted = count(findings, ConformanceFinding.Kind.CONTRADICTED);
        var different = count(findings, ConformanceFinding.Kind.DIFFERENT);
        var notInSchema = count(findings, ConformanceFinding.Kind.NOT_IN_SCHEMA);
        // Only what both sides state is a question of agreement. Counting the schema's whole
        // surface here scored silence as disagreement, which is how a 55-line cross-profile file
        // came to read as "0 of 49 agree".
        var compared = overlap(implied.keySet(), stated);

        return ConformanceReport.builder()
                .documentId(documentId)
                .documentName(documentName)
                .documents(List.copyOf(documentRefs))
                .conforms(contradicted == 0 && different == 0 && notInSchema == 0)
                .compared(compared)
                .agreeing(compared - contradicted - different)
                .impliedBySchema(implied.size())
                .stated(stated.size())
                .contradictedCount(contradicted)
                .differentCount(different)
                .missingInDocumentCount(
                        count(findings, ConformanceFinding.Kind.MISSING_IN_DOCUMENT))
                .notInSchemaCount(notInSchema)
                .findings(findings)
                .build();
    }

    /** The schema's side, from the cache unless some graph of the workspace has changed. */
    private Implied impliedFor(GraphIdentifier graphIdentifier, Set<String> targetedClasses) {
        var key =
                new CacheKey(
                        SessionContext.getSessionId(),
                        graphIdentifier.datasetName(),
                        graphIdentifier.graphUri(),
                        graphVersions(graphIdentifier.datasetName()),
                        targetedClasses);
        synchronized (cache) {
            var cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        var implied = generate(graphIdentifier, targetedClasses);
        // Generation reads the graphs again; a commit in between means the result describes newer
        // content than the key, and filing it under the older versions would outlive an undo.
        if (key.graphVersions().equals(graphVersions(graphIdentifier.datasetName()))) {
            synchronized (cache) {
                cache.put(key, implied);
            }
        }
        return implied;
    }

    private Implied generate(GraphIdentifier graphIdentifier, Set<String> targetedClasses) {
        var own = readGraph(graphIdentifier);
        var ownProperties = subjectsOf(own, RDFS.domain.asNode());
        var ownClasses = ClassHierarchy.of(own);

        // This graph's own statements, then everything else the workspace says — except about
        // the properties this graph declares, which it defines for itself, and about whether its
        // own classes are abstract. Profiles disagree on that: SteadyStateHypothesis marks
        // cim:Equipment concrete, Equipment does not, and taking SSH's word held the Equipment
        // constraints to account for rules on an abstract class no official file states.
        var ownClassUris = ownClassesOf(own);
        var stereotype = CIMS.stereotype.asNode();
        var union = copyOf(own);
        for (String graphUri : databasePort.listGraphUris(graphIdentifier.datasetName())) {
            if (graphUri.equals(graphIdentifier.graphUri())) {
                continue;
            }
            var other = readGraph(new GraphIdentifier(graphIdentifier.datasetName(), graphUri));
            other.find()
                    .forEachRemaining(
                            triple -> {
                                var subject = triple.getSubject();
                                var ownProperty =
                                        subject.isURI() && ownProperties.contains(subject.getURI());
                                var ownClassStereotype =
                                        subject.isURI()
                                                && stereotype.equals(triple.getPredicate())
                                                && ownClassUris.contains(subject.getURI());
                                if (!ownProperty && !ownClassStereotype) {
                                    union.add(triple);
                                }
                            });
        }

        var ontology = ModelFactory.createModelForGraph(union);
        ontology.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));
        var shapes =
                new SHACLFromCIMGenerator(
                                ontology,
                                PrefixEntry.create(RDFA.NS_PREFIX_SHACL, RDFA.NS_URI_SHACL),
                                true)
                        .generate(
                                instantiableClass ->
                                        targetedClasses.contains(instantiableClass.getURI())
                                                || ownClasses.declares(instantiableClass.getURI()))
                        .getGraph();
        return new Implied(
                EffectiveConstraints.of(shapes),
                ClassHierarchy.of(union),
                ownClassUris,
                ownProperties,
                subjectsOf(union, RDFS.domain.asNode()));
    }

    private static Set<String> ownClassesOf(Graph own) {
        return own.stream(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                .map(triple -> triple.getSubject())
                .filter(Node::isURI)
                .map(Node::getURI)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The classes the documents target, which the comparison has to have the schema's word on. */
    private static Set<String> targetedClasses(Iterable<Graph> documents) {
        var targets = new TreeSet<String>();
        documents.forEach(
                document ->
                        document.stream(Node.ANY, Shacl.TARGET_CLASS, Node.ANY)
                                .map(triple -> triple.getObject())
                                .filter(Node::isURI)
                                .forEach(target -> targets.add(target.getURI())));
        return Set.copyOf(targets);
    }

    private static Set<String> subjectsOf(Graph graph, Node predicate) {
        return graph.stream(Node.ANY, predicate, Node.ANY)
                .map(triple -> triple.getSubject())
                .filter(Node::isURI)
                .map(Node::getURI)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Committed version id per graph, which together identify the workspace's schema. */
    private Map<String, UUID> graphVersions(String datasetName) {
        var versions = new LinkedHashMap<String, UUID>();
        for (String graphUri : databasePort.listGraphUris(datasetName)) {
            var identifier = new GraphIdentifier(datasetName, graphUri);
            try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.READ)) {
                versions.put(graphUri, ctx.getRdfGraphVersion());
            }
        }
        return versions;
    }

    private Graph readGraph(GraphIdentifier identifier) {
        try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.READ)) {
            return copyOf(ctx.getRdfGraph());
        }
    }

    private static int count(List<ConformanceFinding> findings, ConformanceFinding.Kind kind) {
        return (int) findings.stream().filter(finding -> finding.getKind() == kind).count();
    }

    private static int overlap(
            Set<EffectiveConstraints.Key> schema, Set<EffectiveConstraints.Key> documents) {
        return (int) schema.stream().filter(documents::contains).count();
    }

    /**
     * A detached copy taken while the read transaction is held.
     *
     * <p>Generating shapes walks the whole ontology and outlives the transaction, and the generator
     * writes its own prefixes onto the model it is given — neither is safe on a live versioned
     * graph.
     */
    private static Graph copyOf(Graph live) {
        var copy = GraphFactory.createDefaultGraph();
        GraphUtil.addInto(copy, live);
        copy.getPrefixMapping().setNsPrefixes(live.getPrefixMapping());
        return copy;
    }
}
