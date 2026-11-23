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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Takes out of the documents' side what is written in a vocabulary the compared schema does not
 * use.
 *
 * <p>Official NC constraints state each rule for every CIM namespace in use — the current one,
 * {@code CIM100} and {@code cim16} — and list each permitted type under all three, so one file
 * checks data of any of those versions. A schema is written in one of them. The rules and types of
 * the others are about data of another version, and comparing them with the schema reads every one
 * as a class the schema is missing, or a type list admitting {@code cim17:Switch} as a
 * contradiction of a range in CIM 18. That holds with a CGMES schema of the other version in the
 * same workspace, too: it is not the schema being compared. A term in a namespace the schema does
 * use is kept, so a misspelling is still reported.
 */
final class OtherVocabularies {

    private OtherVocabularies() {}

    /**
     * The namespaces a schema is written in: those of its classes and properties, and of the
     * classes its properties belong to and point to, which a profile extending another one names
     * without declaring them.
     */
    static Set<String> of(Graph schema) {
        var namespaces = new HashSet<String>();
        schema.stream(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                .forEach(triple -> addNamespace(namespaces, triple.getSubject()));
        for (var predicate :
                Set.of(RDFS.domain.asNode(), RDFS.range.asNode(), CIMS.datatype.asNode())) {
            schema.stream(Node.ANY, predicate, Node.ANY)
                    .forEach(
                            triple -> {
                                addNamespace(namespaces, triple.getSubject());
                                addNamespace(namespaces, triple.getObject());
                            });
        }
        return namespaces;
    }

    private static void addNamespace(Set<String> namespaces, Node node) {
        if (node.isURI()) {
            namespaces.add(namespaceOf(node.getURI()));
        }
    }

    /**
     * @param vocabulary the namespaces the compared schema is written in; empty when it declares
     *     nothing, and there is no telling one vocabulary from another
     */
    static EffectiveConstraints.Asserted leaveOut(
            EffectiveConstraints.Asserted asserted, Set<String> vocabulary) {
        if (vocabulary.isEmpty()) {
            return asserted;
        }
        return new EffectiveConstraints.Asserted(
                inKnown(asserted.constraints(), vocabulary),
                inKnown(asserted.advisory(), vocabulary),
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
     * such types says nothing this schema can check, and is read as stating no type at all. A list
     * that names no type at all admits nothing, and stays as it is.
     */
    private static EffectiveConstraints.Constraint withKnownTypes(
            EffectiveConstraints.Constraint constraint, Set<String> known) {
        var classes = known(constraint.valueClasses(), known);
        var types = constraint.valueTypes();
        if (types != null && !types.isEmpty()) {
            types = known(types, known);
            if (types.isEmpty()) {
                types = null;
            }
        }
        return new EffectiveConstraints.Constraint(
                constraint.minCount(),
                constraint.maxCount(),
                constraint.dataTypes(),
                classes,
                types,
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
