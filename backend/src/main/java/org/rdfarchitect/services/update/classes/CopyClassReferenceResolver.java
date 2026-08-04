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
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
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

@Service
@RequiredArgsConstructor
public class CopyClassReferenceResolver {

    private final DatabasePort databasePort;

    public List<CopyClassReference> resolve(
            List<CopyClassSource> sources, List<CIMClassUMLAdapted> sourceClasses) {

        var urisByGraph =
                new LinkedHashMap<GraphIdentifier, Map<CopyClassReference.Kind, Set<URI>>>();
        var usedBy = new UsagesByReference();
        for (var i = 0; i < sources.size(); i++) {
            var urisByKind =
                    urisByGraph.computeIfAbsent(
                            sources.get(i).graphIdentifier(),
                            graphIdentifier -> new EnumMap<>(CopyClassReference.Kind.class));
            var sourceClass = sourceClasses.get(i);
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
        resolved.forEach((uri, reference) -> reference.usedBy().putAll(usedBy.of(uri)));
        return List.copyOf(resolved.values());
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
            CIMClassUMLAdapted sourceClass, CopyClassReference.Kind kind) {
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
                dataTypeUris(model, referencedClass),
                EnumSet.noneOf(CopyClassReference.Kind.class));
    }

    private Set<URI> dataTypeUris(Model model, ICIMClass referencedClass) {
        var uris = new LinkedHashSet<URI>();
        for (var attribute : referencedClass.getAttributes()) {
            var resource = model.getResource(attribute.getUri().toString());
            if (resource.hasProperty(CIMS.datatype) || resource.hasProperty(RDFS.range)) {
                uris.add(attribute.getDataType().getUri());
            }
        }
        return uris;
    }

    private Map<URI, Set<CopyClassReference.Usage>> dataTypeUsages(CIMClassUMLAdapted sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        for (var attribute : sourceClass.getAttributes()) {
            var dataType = attribute.getDataType();
            if (dataType != null) {
                addUsage(usages, dataType.getUri(), usage(sourceClass, attribute.getLabel()));
            }
        }
        return usages;
    }

    private Map<URI, Set<CopyClassReference.Usage>> associationTargetUsages(
            CIMClassUMLAdapted sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        for (var pair : ClassAssociations.ownedBy(sourceClass)) {
            var range = pair.getFrom().getRange();
            if (range != null) {
                addUsage(usages, range.getUri(), usage(sourceClass, pair.getFrom().getLabel()));
            }
        }
        return usages;
    }

    private Map<URI, Set<CopyClassReference.Usage>> superClassUsages(
            CIMClassUMLAdapted sourceClass) {
        var usages = new LinkedHashMap<URI, Set<CopyClassReference.Usage>>();
        var superClass = sourceClass.getSuperClass();
        if (superClass != null) {
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

    private CopyClassReference.Usage usage(CIMClassUMLAdapted sourceClass, RDFSLabel memberLabel) {
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
