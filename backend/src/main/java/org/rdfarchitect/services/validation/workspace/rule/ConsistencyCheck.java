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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Answers the question most workspace checks come down to: the same URI exists in several schemas,
 * do the schemas describe it the same way? A rule hands over the resources to look at and one or
 * more aspects, an aspect being a signature to compare plus the message and severity to report when
 * the signatures differ.
 *
 * <p>A resource that cannot be read, for instance an attribute without a data type, is reported as
 * an unreadable resource and left out of the comparison, so broken data never stops a rule.
 *
 * <p>Signatures are compared with {@code equals}, so they have to be values with proper equality
 * such as strings or sets of strings, never facade DTOs.
 */
public final class ConsistencyCheck<T extends ICIMResource> {

    private final String ruleId;

    private final IssueCollector issues;

    private final List<Aspect<T>> aspects = new ArrayList<>();

    private record Aspect<T>(Severity severity, String message, Function<T, ?> signature) {}

    private ConsistencyCheck(String ruleId, IssueCollector issues) {
        this.ruleId = ruleId;
        this.issues = issues;
    }

    /**
     * Starts a check that reports its findings under the id of {@code rule}.
     *
     * @return the check, to add aspects to and run
     */
    public static <T extends ICIMResource> ConsistencyCheck<T> of(
            WorkspaceValidationRule rule, IssueCollector issues) {
        return new ConsistencyCheck<>(rule.id(), issues);
    }

    /**
     * Narrows candidates down to the URIs where at least one occurrence matches, for instance the
     * classes that are a datatype in some schema.
     *
     * @return the matching candidates, in the order they came in
     */
    public static <T extends ICIMResource> Map<String, List<Occurrence<T>>> whereAny(
            WorkspaceValidationRule rule,
            IssueCollector issues,
            Map<String, List<Occurrence<T>>> candidates,
            Predicate<T> predicate) {
        var filtered = new LinkedHashMap<String, List<Occurrence<T>>>();
        candidates.forEach(
                (uri, occurrences) -> {
                    if (anyMatches(rule, issues, uri, occurrences, predicate)) {
                        filtered.put(uri, occurrences);
                    }
                });
        return filtered;
    }

    private static <T extends ICIMResource> boolean anyMatches(
            WorkspaceValidationRule rule,
            IssueCollector issues,
            String uri,
            List<Occurrence<T>> occurrences,
            Predicate<T> predicate) {
        var matches = false;
        for (var occurrence : occurrences) {
            try {
                matches |= predicate.test(occurrence.resource());
            } catch (RuntimeException e) {
                issues.reportUnreadable(rule.id(), uri, occurrence, e);
            }
        }
        return matches;
    }

    /**
     * Adds one thing to compare, such as the data type of an attribute.
     *
     * @param severity how bad it is when the signatures differ
     * @param message what to report then
     * @param signature the value to compare across the schemas
     * @return this check, so aspects can be chained
     */
    public ConsistencyCheck<T> aspect(Severity severity, String message, Function<T, ?> signature) {
        aspects.add(new Aspect<>(severity, message, signature));
        return this;
    }

    /**
     * Compares every aspect for every candidate and reports what differs.
     *
     * @param candidates the resources by URI, usually one of the shared views of the workspace
     *     index
     */
    public void run(Map<String, List<Occurrence<T>>> candidates) {
        candidates.forEach(
                (uri, occurrences) -> aspects.forEach(aspect -> check(uri, occurrences, aspect)));
    }

    private void check(String uri, List<Occurrence<T>> occurrences, Aspect<T> aspect) {
        var signatures = new HashSet<>();
        var readable = new ArrayList<IssueOccurrenceDTO>();
        for (var occurrence : occurrences) {
            try {
                var signature = aspect.signature().apply(occurrence.resource());
                signatures.add(signature);
                readable.add(issues.occurrence(occurrence, signature));
            } catch (RuntimeException e) {
                issues.reportUnreadable(ruleId, uri, occurrence, e);
            }
        }
        if (signatures.size() > 1) {
            issues.report(ruleId, aspect.severity(), uri, aspect.message(), readable);
        }
    }
}
