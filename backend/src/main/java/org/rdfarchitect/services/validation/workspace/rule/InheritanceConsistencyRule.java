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

import org.rdfarchitect.api.dto.validation.workspace.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
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

    private static final Severity SUPERCLASS_DIFFERS_SEVERITY = Severity.ERROR;

    private static final Severity SUPERCLASS_MISSING_SEVERITY = Severity.INFO;

    private static final Severity MULTIPLE_INHERITANCE_SEVERITY = Severity.WARNING;

    private static final Severity CYCLE_SEVERITY = Severity.ERROR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var superClassesByOccurrence = readSuperClasses(context, issues);
        superClassesByOccurrence.forEach(
                (uri, superClasses) -> {
                    checkMultipleInheritance(uri, superClasses, issues);
                    if (superClasses.size() > 1) {
                        checkConsistency(uri, superClasses, issues);
                    }
                });
        checkCycles(superClassesByOccurrence, issues);
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
                                    issues.reportUnreadable(ID, uri, occurrence, e);
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
            IssueCollector issues) {
        superClasses.forEach(
                (occurrence, uris) -> {
                    if (uris.size() > 1) {
                        issues.report(
                                ID,
                                MULTIPLE_INHERITANCE_SEVERITY,
                                uri,
                                "Class has more than one superclass.",
                                List.of(issues.occurrence(occurrence, uris)));
                    }
                });
    }

    private void checkConsistency(
            String uri,
            Map<Occurrence<ICIMClass>, Set<String>> superClasses,
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
        var occurrences = toOccurrences(superClasses, issues);
        if (declared.size() > 1) {
            issues.report(
                    ID,
                    SUPERCLASS_DIFFERS_SEVERITY,
                    uri,
                    "Class inherits from different superclasses in different schemas.",
                    occurrences);
        }
        if (missing && !declared.isEmpty()) {
            issues.report(
                    ID,
                    SUPERCLASS_MISSING_SEVERITY,
                    uri,
                    "Class has a superclass in some schemas but none in others.",
                    occurrences);
        }
    }

    private static List<IssueOccurrenceDTO> toOccurrences(
            Map<Occurrence<ICIMClass>, Set<String>> superClasses, IssueCollector issues) {
        var occurrences = new ArrayList<IssueOccurrenceDTO>();
        superClasses.forEach(
                (occurrence, uris) -> occurrences.add(issues.occurrence(occurrence, uris)));
        return occurrences;
    }

    private void checkCycles(
            Map<String, Map<Occurrence<ICIMClass>, Set<String>>> superClassesByOccurrence,
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
            reportCycle(cycle, superClassesByOccurrence, issues);
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
            IssueCollector issues) {
        var occurrences = new ArrayList<IssueOccurrenceDTO>();
        for (var i = 0; i < cycle.size(); i++) {
            var uri = cycle.get(i);
            var next = cycle.get((i + 1) % cycle.size());
            superClassesByOccurrence
                    .getOrDefault(uri, Map.of())
                    .forEach(
                            (occurrence, uris) -> {
                                if (uris.contains(next)) {
                                    occurrences.add(issues.occurrence(occurrence, next));
                                }
                            });
        }
        var path = new ArrayList<>(cycle);
        path.add(cycle.getFirst());
        issues.report(
                ID,
                CYCLE_SEVERITY,
                cycle.getFirst(),
                "Inheritance cycle: " + String.join(" -> ", path),
                occurrences);
    }
}
