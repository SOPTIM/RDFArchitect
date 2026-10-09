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

import org.rdfarchitect.services.shacl.effective.ClassHierarchy;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Takes out of the documents' side what is written in a vocabulary no schema of the workspace uses.
 *
 * <p>Official NC constraints state each rule for every CIM namespace in use — the current one,
 * {@code CIM100} and {@code cim16} — and list each permitted type under all three, so one file
 * checks data of any of those versions. Only one of them can be in the workspace. Compared as they
 * stand, the other versions' shapes read as 702 classes no schema declares, and a type list that
 * also admits {@code cim16:Terminal} read as a contradiction of one that admits {@code
 * cim:ACDCTerminal}. There is nothing in the workspace to compare them with, so they are left out;
 * a term in a namespace the workspace does use is kept, so a misspelling is still reported.
 */
final class OtherVocabularies {

    private OtherVocabularies() {}

    static EffectiveConstraints.Asserted leaveOut(
            EffectiveConstraints.Asserted asserted,
            ClassHierarchy hierarchy,
            Set<String> properties) {
        var known = new HashSet<String>();
        hierarchy.declared().forEach(iri -> known.add(namespaceOf(iri)));
        properties.forEach(iri -> known.add(namespaceOf(iri)));
        if (known.isEmpty()) {
            // Without a schema there is no telling one vocabulary from another.
            return asserted;
        }
        return new EffectiveConstraints.Asserted(
                inKnown(asserted.constraints(), known),
                inKnown(asserted.advisory(), known),
                asserted.statedIn());
    }

    private static Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> inKnown(
            Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint> constraints,
            Set<String> known) {
        var kept = new LinkedHashMap<EffectiveConstraints.Key, EffectiveConstraints.Constraint>();
        constraints.forEach(
                (key, constraint) -> {
                    // By target class only: a path such as rdf:type is in no schema namespace.
                    if (known.contains(namespaceOf(key.targetClass()))) {
                        kept.put(key, withKnownTypes(constraint, known));
                    }
                });
        return kept;
    }

    /**
     * The constraint with the value types of other vocabularies taken out. A type list naming only
     * such types says nothing this workspace can check, and is read as stating no type at all.
     */
    private static EffectiveConstraints.Constraint withKnownTypes(
            EffectiveConstraints.Constraint constraint, Set<String> known) {
        var classes = known(constraint.valueClasses(), known);
        var types = constraint.valueTypes() == null ? null : known(constraint.valueTypes(), known);
        return new EffectiveConstraints.Constraint(
                constraint.minCount(),
                constraint.maxCount(),
                constraint.dataTypes(),
                classes,
                types == null || types.isEmpty() ? null : types,
                constraint.nodeKinds(),
                constraint.allowedValues());
    }

    private static Set<String> known(Set<String> iris, Set<String> known) {
        var kept = new LinkedHashSet<String>();
        for (var iri : iris) {
            if (known.contains(namespaceOf(iri))) {
                kept.add(iri);
            }
        }
        return kept;
    }

    /** Up to and including the last {@code #} or {@code /}, which is how CIM namespaces end. */
    private static String namespaceOf(String iri) {
        return iri.substring(0, Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/')) + 1);
    }
}
