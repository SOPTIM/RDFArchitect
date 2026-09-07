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

package org.rdfarchitect.services.shacl.terms;

import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;
import de.soptim.opencgmes.cimvocabcheck.core.schema.SchemaIndex;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.shacl.validation.SchemaIndexCache;
import org.rdfarchitect.shacl.dto.SchemaTerm;
import org.rdfarchitect.shacl.dto.SchemaTermDetail;
import org.rdfarchitect.shacl.dto.SchemaTerms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the workspace's CIM terms out of the schema index that already backs shape validation.
 *
 * <p>This is the part of an IDE experience that is usually a language server. It is not one here
 * because there would be nothing for the protocol to carry: the index is built in this process by
 * {@link SchemaIndexCache}, and every question hover and completion ask — what is declared, what is
 * it called, what does it comment, what may it be used on — is a method on {@link SchemaIndex}.
 */
@RequiredArgsConstructor
public class SchemaTermsService implements SchemaTermsUseCase {

    private final DatabasePort databasePort;

    private final SchemaIndexCache schemaIndexCache;

    /** Where a term can be opened for editing, and which diagram shows it. */
    private record ClassLocation(String graphUri, UUID classUUID, UUID packageUUID) {}

    @Override
    public SchemaTerms listTerms(GraphIdentifier graphIdentifier) {
        var index = schemaIndexCache.apiFor(graphIdentifier.datasetName()).schemaIndex();
        var terms = new ArrayList<SchemaTerm>();
        collect(terms, index, index.allClasses(), SchemaTerm.Kind.CLASS);
        collect(terms, index, index.allProperties(), SchemaTerm.Kind.PROPERTY);
        collect(terms, index, index.allEnumMembers(), SchemaTerm.Kind.ENUM_MEMBER);
        terms.sort(Comparator.comparing(SchemaTerm::getIri));
        return SchemaTerms.builder().profiles(iris(index.getAllProfiles())).terms(terms).build();
    }

    @Override
    public SchemaTermDetail detailOf(GraphIdentifier graphIdentifier, String iri) {
        var index = schemaIndexCache.apiFor(graphIdentifier.datasetName()).schemaIndex();
        var term = NodeFactory.createURI(iri);

        var kind = kindOf(index, term);
        if (kind == null) {
            return null;
        }
        var profiles = profilesOf(index, term, kind);
        var isProperty = kind == SchemaTerm.Kind.PROPERTY;
        var domains = isProperty ? index.domainsOf(term, profiles) : Set.<Node>of();
        var ranges = isProperty ? index.rangesOf(term, profiles) : Set.<Node>of();
        var multiplicity = index.multiplicityOf(term, profiles);
        var location = locate(graphIdentifier.datasetName(), kind, term, domains);

        return SchemaTermDetail.builder()
                .kind(kind)
                .iri(iri)
                .namespace(namespaceOf(iri))
                .localName(localNameOf(iri))
                .label(index.labelOf(term, profiles).orElse(null))
                .comment(index.commentOf(term, profiles).orElse(null))
                .domains(uris(domains))
                .ranges(uris(ranges))
                .multiplicity(multiplicity.map(m -> m.display()).orElse(null))
                .minCount(multiplicity.map(m -> m.min()).orElse(null))
                .maxCount(multiplicity.map(m -> m.max()).orElse(null))
                .profiles(iris(profiles))
                .graphUri(location.map(ClassLocation::graphUri).orElse(null))
                .classUUID(location.map(ClassLocation::classUUID).orElse(null))
                .packageUUID(location.map(ClassLocation::packageUUID).orElse(null))
                .build();
    }

    // -------------------------------------------------------------------------
    // The index
    // -------------------------------------------------------------------------

    private static void collect(
            List<SchemaTerm> terms, SchemaIndex index, Set<Node> nodes, SchemaTerm.Kind kind) {
        for (Node node : nodes) {
            if (!node.isURI()) {
                continue;
            }
            var iri = node.getURI();
            var localName = localNameOf(iri);
            var profiles = profilesOf(index, node, kind);
            var label = index.labelOf(node, profiles).orElse(null);
            terms.add(
                    SchemaTerm.builder()
                            .kind(kind)
                            .iri(iri)
                            .namespace(namespaceOf(iri))
                            .localName(localName)
                            .label(localName.equals(label) ? null : label)
                            .domain(
                                    kind == SchemaTerm.Kind.PROPERTY
                                            ? firstUri(index.domainsOf(node, profiles))
                                            : null)
                            .build());
        }
    }

    /**
     * What the index declares the term as, or {@code null} when it declares it at all.
     *
     * <p>Properties are asked about first, matching how the term was written: a name used in
     * predicate position is a property even if something else of that name exists.
     */
    private static SchemaTerm.Kind kindOf(SchemaIndex index, Node term) {
        if (!index.findProperty(term).isEmpty()) {
            return SchemaTerm.Kind.PROPERTY;
        }
        if (!index.findClass(term).isEmpty()) {
            return SchemaTerm.Kind.CLASS;
        }
        if (!index.findEnumMember(term).isEmpty()) {
            return SchemaTerm.Kind.ENUM_MEMBER;
        }
        return null;
    }

    private static List<VersionIri> profilesOf(SchemaIndex index, Node term, SchemaTerm.Kind kind) {
        return switch (kind) {
            case CLASS -> index.findClass(term);
            case PROPERTY -> index.findProperty(term);
            case ENUM_MEMBER -> index.findEnumMember(term);
        };
    }

    // -------------------------------------------------------------------------
    // Where a term can be opened
    // -------------------------------------------------------------------------

    /**
     * The class to open for a term: the class itself, or for a property the class it is on.
     *
     * <p>A property is edited as a row of its class rather than on its own, so its domain is the
     * useful destination. An enum member has no such route — the index knows it is a member but not
     * of which class — so following one is not offered rather than guessed from its name.
     */
    private Optional<ClassLocation> locate(
            String datasetName, SchemaTerm.Kind kind, Node term, Set<Node> domains) {
        return switch (kind) {
            case CLASS -> locateClass(datasetName, term);
            case PROPERTY ->
                    domains.stream()
                            .filter(Node::isURI)
                            .sorted(Comparator.comparing(Node::getURI))
                            .map(domain -> locateClass(datasetName, domain))
                            .flatMap(Optional::stream)
                            .findFirst();
            case ENUM_MEMBER -> Optional.empty();
        };
    }

    /**
     * The graph and id under which the workspace holds a class, if it holds one.
     *
     * <p>Found by the {@code rdfa:uuid} the in-memory database stamps onto every typed resource on
     * commit, which is the same id the class editor is addressed by. A term that only a read-only
     * imported profile declares has none, and is simply not followable.
     *
     * <p>The package is read in the same pass. It is what tells the caller which diagram to put on
     * screen, and looking it up separately would mean walking every graph a second time.
     */
    private Optional<ClassLocation> locateClass(String datasetName, Node classNode) {
        for (String graphUri : databasePort.listGraphUris(datasetName)) {
            var identifier = new GraphIdentifier(datasetName, graphUri);
            try (var ctx = databasePort.getGraphWithContext(identifier).begin(ReadWrite.READ)) {
                var graph = ctx.getRdfGraph();
                var uuid = uuidOf(graph, classNode);
                if (uuid.isPresent()) {
                    return Optional.of(
                            new ClassLocation(
                                    graphUri,
                                    uuid.get(),
                                    packageOf(graph, classNode).orElse(null)));
                }
            }
        }
        return Optional.empty();
    }

    /** The id of the package the class declares itself part of, if it declares one. */
    private static Optional<UUID> packageOf(Graph graph, Node classNode) {
        return graph.stream(classNode, CIMS.belongsToCategory.asNode(), Node.ANY)
                .map(Triple::getObject)
                .filter(Node::isURI)
                .flatMap(category -> uuidOf(graph, category).stream())
                .findFirst();
    }

    private static Optional<UUID> uuidOf(Graph graph, Node subject) {
        return graph.stream(subject, RDFA.uuid.asNode(), Node.ANY)
                .map(triple -> asUuid(triple.getObject()))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static UUID asUuid(Node object) {
        if (!object.isLiteral()) {
            return null;
        }
        try {
            return UUID.fromString(object.getLiteralLexicalForm());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // IRIs
    // -------------------------------------------------------------------------

    /** Everything up to and including the last {@code #} or {@code /}. */
    static String namespaceOf(String iri) {
        return iri.substring(0, splitAt(iri) + 1);
    }

    static String localNameOf(String iri) {
        return iri.substring(splitAt(iri) + 1);
    }

    private static int splitAt(String iri) {
        return Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
    }

    private static List<String> iris(Collection<VersionIri> profiles) {
        return profiles.stream().map(VersionIri::iri).toList();
    }

    private static String firstUri(Set<Node> nodes) {
        return nodes.stream()
                .filter(Node::isURI)
                .map(Node::getURI)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private static List<String> uris(Set<Node> nodes) {
        return nodes.stream().filter(Node::isURI).map(Node::getURI).sorted().toList();
    }
}
