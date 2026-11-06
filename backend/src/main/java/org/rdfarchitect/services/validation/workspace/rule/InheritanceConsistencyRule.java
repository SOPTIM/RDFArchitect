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

package org.rdfarchitect.services.validation.workspace.rule;

import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.IssueOccurrences;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class InheritanceConsistencyRule implements WorkspaceValidationRule {

    private static final String ID = "inheritance-consistency";

    private static final ValidationSeverity SUPERCLASS_DIFFERS_SEVERITY = ValidationSeverity.ERROR;

    private static final ValidationSeverity SUPERCLASS_MISSING_SEVERITY = ValidationSeverity.INFO;

    private static final ValidationSeverity MULTIPLE_INHERITANCE_SEVERITY =
            ValidationSeverity.WARNING;

    private static final ValidationSeverity CYCLE_SEVERITY = ValidationSeverity.ERROR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Inheritance";
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var superClassesByOccurrence = readSuperClasses(context, issues);
        var occurrences = context.getOccurrences();
        superClassesByOccurrence.forEach(
                (uri, superClasses) -> {
                    checkMultipleInheritance(uri, superClasses, occurrences, issues);
                    if (superClasses.size() > 1) {
                        checkConsistency(uri, superClasses, occurrences, issues);
                    }
                });
        checkCycles(superClassesByOccurrence, occurrences, issues);
    }

    private Map<String, Map<Occurrence<ICIMClass>, Set<String>>> readSuperClasses(
            WorkspaceValidationContext context, IssueCollector issues) {
        var result = new LinkedHashMap<String, Map<Occurrence<ICIMClass>, Set<String>>>();
        context.getIndex()
                .classes()
                .forEach(
                        (uri, occurrences) -> {
                            var superClasses =
                                    new LinkedHashMap<Occurrence<ICIMClass>, Set<String>>();
                            for (var occurrence : occurrences) {
                                try {
                                    superClasses.put(
                                            occurrence, superClassUris(occurrence.resource()));
                                } catch (RuntimeException e) {
                                    issues.reportUnreadable(this, uri, occurrence, e);
                                }
                            }
                            result.put(uri, superClasses);
                        });
        return result;
    }

    private static Set<String> superClassUris(ICIMClass cimClass) {
        var uris = new TreeSet<String>();
        for (var superClass : cimClass.getSuperClasses()) {
            uris.add(superClass.getUri().toString());
        }
        return uris;
    }

    private void checkMultipleInheritance(
            String uri,
            Map<Occurrence<ICIMClass>, Set<String>> superClasses,
            IssueOccurrences occurrences,
            IssueCollector issues) {
        var affected = new LinkedHashMap<Occurrence<ICIMClass>, Set<String>>();
        superClasses.forEach(
                (occurrence, uris) -> {
                    if (uris.size() > 1) {
                        affected.put(occurrence, uris);
                    }
                });
        if (!affected.isEmpty()) {
            issues.report(
                    this,
                    MULTIPLE_INHERITANCE_SEVERITY,
                    uri,
                    "Class has more than one superclass.",
                    toOccurrences(affected, occurrences));
        }
    }

    private void checkConsistency(
            String uri,
            Map<Occurrence<ICIMClass>, Set<String>> superClasses,
            IssueOccurrences occurrences,
            IssueCollector issues) {
        var declared = new HashSet<Set<String>>();
        var missing = false;
        for (var uris : superClasses.values()) {
            if (uris.isEmpty()) {
                missing = true;
            } else {
                declared.add(uris);
            }
        }
        var issueOccurrences = toOccurrences(superClasses, occurrences);
        if (declared.size() > 1) {
            issues.report(
                    this,
                    SUPERCLASS_DIFFERS_SEVERITY,
                    uri,
                    "Class inherits from different superclasses in different schemas.",
                    issueOccurrences);
        }
        if (missing && !declared.isEmpty()) {
            issues.report(
                    this,
                    SUPERCLASS_MISSING_SEVERITY,
                    uri,
                    "Class has a superclass in some schemas but none in others.",
                    issueOccurrences);
        }
    }

    private static List<IssueOccurrenceDTO> toOccurrences(
            Map<Occurrence<ICIMClass>, Set<String>> superClasses, IssueOccurrences occurrences) {
        var issueOccurrences = new ArrayList<IssueOccurrenceDTO>();
        superClasses.forEach(
                (occurrence, uris) -> issueOccurrences.add(occurrences.of(occurrence, uris)));
        return issueOccurrences;
    }

    private void checkCycles(
            Map<String, Map<Occurrence<ICIMClass>, Set<String>>> superClassesByOccurrence,
            IssueOccurrences occurrences,
            IssueCollector issues) {
        var graph = new HashMap<String, Set<String>>();
        superClassesByOccurrence.forEach(
                (uri, superClasses) ->
                        superClasses
                                .values()
                                .forEach(
                                        uris ->
                                                graph.computeIfAbsent(uri, _ -> new TreeSet<>())
                                                        .addAll(uris)));

        var reported = new HashSet<List<String>>();
        var finished = new HashSet<String>();
        for (var start : superClassesByOccurrence.keySet()) {
            findCycles(start, graph, new ArrayList<>(), new HashSet<>(), finished, reported);
        }
        for (var cycle : reported) {
            reportCycle(cycle, superClassesByOccurrence, occurrences, issues);
        }
    }

    private static void findCycles(
            String uri,
            Map<String, Set<String>> graph,
            List<String> path,
            Set<String> onPath,
            Set<String> finished,
            Set<List<String>> cycles) {
        if (onPath.contains(uri)) {
            cycles.add(normalize(path.subList(path.indexOf(uri), path.size())));
            return;
        }
        if (finished.contains(uri)) {
            return;
        }
        path.add(uri);
        onPath.add(uri);
        for (var superClass : graph.getOrDefault(uri, Set.of())) {
            findCycles(superClass, graph, path, onPath, finished, cycles);
        }
        path.removeLast();
        onPath.remove(uri);
        finished.add(uri);
    }

    private static List<String> normalize(List<String> cycle) {
        var start = cycle.indexOf(Collections.min(cycle));
        var normalized = new ArrayList<String>(cycle.size());
        for (var i = 0; i < cycle.size(); i++) {
            normalized.add(cycle.get((start + i) % cycle.size()));
        }
        return List.copyOf(normalized);
    }

    private void reportCycle(
            List<String> cycle,
            Map<String, Map<Occurrence<ICIMClass>, Set<String>>> superClassesByOccurrence,
            IssueOccurrences occurrences,
            IssueCollector issues) {
        var issueOccurrences = new ArrayList<IssueOccurrenceDTO>();
        for (var i = 0; i < cycle.size(); i++) {
            var uri = cycle.get(i);
            var next = cycle.get((i + 1) % cycle.size());
            superClassesByOccurrence
                    .getOrDefault(uri, Map.of())
                    .forEach(
                            (occurrence, uris) -> {
                                if (uris.contains(next)) {
                                    issueOccurrences.add(occurrences.of(occurrence, next));
                                }
                            });
        }
        var path = new ArrayList<>(cycle);
        path.add(cycle.getFirst());
        issues.report(
                this,
                CYCLE_SEVERITY,
                cycle.getFirst(),
                "Inheritance cycle: " + String.join(" -> ", path),
                issueOccurrences);
    }
}
