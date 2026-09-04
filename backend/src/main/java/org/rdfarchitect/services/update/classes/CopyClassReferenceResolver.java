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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.facade.CIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.update.classes.CopyClassSourceReader.ResolvedSource;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopyClassReferenceResolver {

    private final DatabasePort databasePort;

    /**
     * Resolves the classes the {@code sources} point at, so that a paste can offer them for the
     * graph they are pasted into. Inheritance is followed all the way up, stopping above every
     * class {@code targetGraphIdentifier} already defines: such a class is not copied, so a super
     * class of it would land in the target with nothing inheriting from it.
     */
    public List<CopyClassReference> resolve(
            List<ResolvedSource> sources, GraphIdentifier targetGraphIdentifier) {

        var urisByGraph =
                new LinkedHashMap<GraphIdentifier, Map<CopyClassReference.Kind, Set<URI>>>();
        var usedBy = new UsagesByReference();
        for (var source : sources) {
            var urisByKind =
                    urisByGraph.computeIfAbsent(
                            source.graphIdentifier(),
                            graphIdentifier -> new EnumMap<>(CopyClassReference.Kind.class));
            var sourceClass = source.cimClass();
            for (var kind : CopyClassReference.Kind.values()) {
                var usages = usagesOf(sourceClass, kind);
                urisByKind
                        .computeIfAbsent(kind, k -> new LinkedHashSet<>())
                        .addAll(usages.keySet());
                usedBy.add(kind, usages);
            }
        }

        var resolved = new LinkedHashMap<URI, CopyClassReference>();
        urisByGraph.forEach(
                (graphIdentifier, urisByKind) ->
                        readReferences(resolved, graphIdentifier, urisByKind));
        resolveSuperClassesOf(resolved, usedBy, targetGraphIdentifier);
        resolved.forEach((uri, reference) -> reference.usedBy().putAll(usedBy.of(uri)));
        return List.copyOf(resolved.values());
    }

    /**
     * Adds the super classes above the ones already resolved, so that a paste offers the whole
     * inheritance chain of a copied class instead of its direct super class alone. Each of them is
     * named after the sub class that inherits from it, and classes already resolved as a reference
     * of another kind only gain the {@link CopyClassReference.Kind#SUPER_CLASS} kind. Walking stops
     * once a round discovers nothing new, which also ends a cyclic hierarchy.
     */
    private void resolveSuperClassesOf(
            Map<URI, CopyClassReference> resolved,
            UsagesByReference usedBy,
            GraphIdentifier targetGraphIdentifier) {

        var frontier =
                resolved.values().stream()
                        .filter(
                                reference ->
                                        reference
                                                .kinds()
                                                .contains(CopyClassReference.Kind.SUPER_CLASS))
                        .toList();
        if (frontier.isEmpty()) {
            return;
        }
        var classesInTargetGraph = classUrisOf(targetGraphIdentifier);
        while (!frontier.isEmpty()) {
            var urisByGraph = new LinkedHashMap<GraphIdentifier, Set<URI>>();
            var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
            for (var reference : frontier) {
                if (reference.superClassUris().isEmpty()
                        || classesInTargetGraph.contains(reference.uri())) {
                    continue;
                }
                urisByGraph
                        .computeIfAbsent(
                                reference.sourceGraph(), graphIdentifier -> new LinkedHashSet<>())
                        .addAll(reference.superClassUris());
                reference
                        .superClassUris()
                        .forEach(
                                uri ->
                                        addUsage(
                                                usages,
                                                uri,
                                                new CopyClassReference.Usage(
                                                        reference.label(), null)));
            }
            usedBy.add(CopyClassReference.Kind.SUPER_CLASS, usages);

            var known = Set.copyOf(resolved.keySet());
            urisByGraph.forEach(
                    (graphIdentifier, uris) ->
                            readReferences(
                                    resolved,
                                    graphIdentifier,
                                    Map.of(CopyClassReference.Kind.SUPER_CLASS, uris)));
            frontier =
                    resolved.entrySet().stream()
                            .filter(entry -> !known.contains(entry.getKey()))
                            .map(Map.Entry::getValue)
                            .toList();
        }
    }

    public List<CopyClassReference> resolveDataTypesOf(List<CopyClassReference> copiedReferences) {
        var resolved = new LinkedHashMap<URI, CopyClassReference>();
        var known =
                copiedReferences.stream()
                        .map(CopyClassReference::uri)
                        .collect(Collectors.toCollection(HashSet::new));

        var frontier =
                copiedReferences.stream()
                        .filter(
                                reference ->
                                        reference.kinds().stream()
                                                .anyMatch(CopyClassReference.Kind::copiesMembers))
                        .toList();
        while (!frontier.isEmpty()) {
            var urisByGraph = new LinkedHashMap<GraphIdentifier, Set<URI>>();
            for (var reference : frontier) {
                var uris = new LinkedHashSet<>(reference.dataTypeUris());
                uris.removeIf(known::contains);
                known.addAll(uris);
                urisByGraph
                        .computeIfAbsent(
                                reference.sourceGraph(), graphIdentifier -> new LinkedHashSet<>())
                        .addAll(uris);
            }

            var discovered = new LinkedHashMap<URI, CopyClassReference>();
            urisByGraph.forEach(
                    (graphIdentifier, uris) ->
                            readReferences(
                                    discovered,
                                    graphIdentifier,
                                    Map.of(CopyClassReference.Kind.DATA_TYPE, uris)));
            resolved.putAll(discovered);
            frontier = List.copyOf(discovered.values());
        }
        return List.copyOf(resolved.values());
    }

    /** Reads the class URIs of a graph in a transaction of its own, closed before the walk. */
    private Set<URI> classUrisOf(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ctx
                    .getRdfGraph()
                    .find(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                    .toList()
                    .stream()
                    .map(Triple::getSubject)
                    .filter(Node::isURI)
                    .map(subject -> new URI(subject.getURI()))
                    .collect(Collectors.toSet());
        }
    }

    public Map<URI, Set<URI>> dataTypeDependenciesOf(List<CopyClassReference> references) {
        var edges = new LinkedHashMap<URI, Set<URI>>();
        references.forEach(reference -> edges.put(reference.uri(), reference.dataTypeUris()));
        resolveDataTypesOf(references)
                .forEach(reference -> edges.putIfAbsent(reference.uri(), reference.dataTypeUris()));

        var dependencies = new LinkedHashMap<URI, Set<URI>>();
        for (var reference : references) {
            dependencies.put(reference.uri(), reachableFrom(reference.uri(), edges));
        }
        return dependencies;
    }

    private Set<URI> reachableFrom(URI start, Map<URI, Set<URI>> edges) {
        var reached = new LinkedHashSet<URI>();
        var queue = new ArrayDeque<>(edges.getOrDefault(start, Set.of()));
        while (!queue.isEmpty()) {
            var uri = queue.poll();
            if (uri.equals(start) || !reached.add(uri)) {
                continue;
            }
            queue.addAll(edges.getOrDefault(uri, Set.of()));
        }
        return reached;
    }

    private Map<URI, Set<CopyClassReference.Usage>> usagesOf(
            ICIMClass sourceClass, CopyClassReference.Kind kind) {
        return switch (kind) {
            case DATA_TYPE -> dataTypeUsages(sourceClass);
            case ASSOCIATION_TARGET -> associationTargetUsages(sourceClass);
            case SUPER_CLASS -> superClassUsages(sourceClass);
        };
    }

    private void readReferences(
            Map<URI, CopyClassReference> resolved,
            GraphIdentifier graphIdentifier,
            Map<CopyClassReference.Kind, Set<URI>> urisByKind) {

        if (urisByKind.values().stream().allMatch(Set::isEmpty)) {
            return;
        }
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            urisByKind.forEach(
                    (kind, uris) -> readReferences(resolved, model, graphIdentifier, uris, kind));
        }
    }

    private void readReferences(
            Map<URI, CopyClassReference> resolved,
            Model model,
            GraphIdentifier graphIdentifier,
            Set<URI> uris,
            CopyClassReference.Kind kind) {

        for (var uri : uris) {
            var reference = resolved.get(uri);
            if (reference == null) {
                reference = readReference(model, graphIdentifier, uri);
                if (reference == null) {
                    continue;
                }
                resolved.put(uri, reference);
            }
            reference.kinds().add(kind);
        }
    }

    private CopyClassReference readReference(
            Model model, GraphIdentifier graphIdentifier, URI uri) {

        var resource = model.getResource(uri.toString());
        if (!resource.hasProperty(RDF.type, RDFS.Class) || !resource.hasProperty(RDFA.uuid)) {
            return null;
        }
        var referencedClass = new CIMClass(graphIdentifier.graphUri(), model, resource);
        return new CopyClassReference(
                graphIdentifier,
                referencedClass.getUuid(),
                referencedClass.getUri(),
                referencedClass.getLabel().getValue(),
                dataTypeUris(referencedClass),
                superClassUris(referencedClass),
                EnumSet.noneOf(CopyClassReference.Kind.class));
    }

    private Set<URI> superClassUris(ICIMClass referencedClass) {
        var uris = new LinkedHashSet<URI>();
        for (var superClass : referencedClass.getSuperClasses()) {
            if (superClass.getUri() != null) {
                uris.add(superClass.getUri());
            }
        }
        return uris;
    }

    private Set<URI> dataTypeUris(ICIMClass referencedClass) {
        var uris = new LinkedHashSet<URI>();
        for (var attribute : referencedClass.getAttributes()) {
            if (attribute.getDataTypeKind() != CIMSDataType.Type.UNKNOWN) {
                uris.add(attribute.getDataType().getUri());
            }
        }
        return uris;
    }

    private Map<URI, Set<CopyClassReference.Usage>> dataTypeUsages(ICIMClass sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        for (var attribute : sourceClass.getAttributes()) {
            if (attribute.getDataTypeKind() != CIMSDataType.Type.UNKNOWN) {
                addUsage(
                        usages,
                        attribute.getDataType().getUri(),
                        usage(sourceClass, attribute.getLabel()));
            }
        }
        return usages;
    }

    private Map<URI, Set<CopyClassReference.Usage>> associationTargetUsages(ICIMClass sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        for (var association : sourceClass.getAssociations()) {
            try {
                addUsage(
                        usages,
                        association.getRange().getUri(),
                        usage(sourceClass, association.getLabel()));
            } catch (IllegalStateException exception) {
                log.warn(
                        "Skipping an association of class '{}' because it is incomplete: {}",
                        sourceClass.getUri(),
                        exception.getMessage());
            }
        }
        return usages;
    }

    private Map<URI, Set<CopyClassReference.Usage>> superClassUsages(ICIMClass sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        for (var superClass : sourceClass.getSuperClasses()) {
            addUsage(usages, superClass.getUri(), usage(sourceClass, null));
        }
        return usages;
    }

    private void addUsage(
            Map<URI, Set<CopyClassReference.Usage>> usages,
            URI uri,
            CopyClassReference.Usage usage) {
        if (uri != null) {
            usages.computeIfAbsent(uri, u -> new LinkedHashSet<>()).add(usage);
        }
    }

    private CopyClassReference.Usage usage(ICIMClass sourceClass, RDFSLabel memberLabel) {
        return new CopyClassReference.Usage(
                sourceClass.getLabel().getValue(),
                memberLabel == null ? null : memberLabel.getValue());
    }

    private static final class UsagesByReference {

        private final Map<URI, Map<CopyClassReference.Kind, Set<CopyClassReference.Usage>>> byUri =
                new LinkedHashMap<>();

        private void add(
                CopyClassReference.Kind kind, Map<URI, Set<CopyClassReference.Usage>> usages) {
            usages.forEach(
                    (uri, entries) ->
                            byUri.computeIfAbsent(
                                            uri, u -> new EnumMap<>(CopyClassReference.Kind.class))
                                    .computeIfAbsent(kind, k -> new LinkedHashSet<>())
                                    .addAll(entries));
        }

        private Map<CopyClassReference.Kind, Set<CopyClassReference.Usage>> of(URI uri) {
            return byUri.getOrDefault(uri, Map.of());
        }
    }
}
