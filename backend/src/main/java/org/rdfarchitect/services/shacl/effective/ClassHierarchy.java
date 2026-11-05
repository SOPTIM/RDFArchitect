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

package org.rdfarchitect.services.shacl.effective;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The class hierarchy of a schema, as far as comparing value types needs it.
 *
 * <p>Generated SHACL states an association's value type as the list of classes an instance may be
 * typed as — the range and every concrete class deriving from it — while official NC files state
 * {@code sh:class} on the range. Both admit the same data, because CIM instances are typed with
 * their concrete class only, so they are compared by the concrete classes they admit rather than by
 * the classes they name.
 */
public final class ClassHierarchy {

    /** No hierarchy at all: every class admits itself and nothing else. */
    public static final ClassHierarchy NONE = new ClassHierarchy(Map.of(), Set.of(), Set.of());

    private static final Node CONCRETE = NodeFactory.createURI(CIMStereotypes.concreteString);

    private final Map<String, Set<String>> subClasses;

    private final Set<String> classes;

    private final Set<String> concrete;

    private ClassHierarchy(
            Map<String, Set<String>> subClasses, Set<String> classes, Set<String> concrete) {
        this.subClasses = subClasses;
        this.classes = classes;
        this.concrete = concrete;
    }

    /** Reads {@code rdfs:subClassOf} and the CIM {@code concrete} stereotype from a schema. */
    public static ClassHierarchy of(Graph schema) {
        var subClasses = new HashMap<String, Set<String>>();
        schema.stream(Node.ANY, RDFS.subClassOf.asNode(), Node.ANY)
                .filter(triple -> triple.getSubject().isURI() && triple.getObject().isURI())
                .forEach(
                        triple ->
                                subClasses
                                        .computeIfAbsent(
                                                triple.getObject().getURI(),
                                                ignored -> new HashSet<>())
                                        .add(triple.getSubject().getURI()));
        var classes = new HashSet<String>();
        schema.stream(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                .filter(triple -> triple.getSubject().isURI())
                .forEach(triple -> classes.add(triple.getSubject().getURI()));
        var concrete = new HashSet<String>();
        schema.stream(Node.ANY, CIMS.stereotype.asNode(), CONCRETE)
                .filter(triple -> triple.getSubject().isURI())
                .forEach(triple -> concrete.add(triple.getSubject().getURI()));
        return new ClassHierarchy(subClasses, classes, concrete);
    }

    /** Whether the schema declares {@code name} as a class. */
    public boolean declares(String name) {
        return classes.contains(name);
    }

    /** Every class the schema declares. */
    public Set<String> declared() {
        return Collections.unmodifiableSet(classes);
    }

    /** Whether the schema declares any class at all, i.e. whether {@link #declares} can say no. */
    public boolean isEmpty() {
        return classes.isEmpty();
    }

    /** Whether {@code sub} is {@code sup} or derives from it. */
    public boolean isSubClassOf(String sub, String sup) {
        return sub.equals(sup) || descendants(sup).contains(sub);
    }

    /**
     * The types an instance of any of {@code named} may carry — each class and everything deriving
     * from it, as {@code sh:class} reads — less the ones the schema knows to be abstract.
     */
    public Set<String> instancesOf(Set<String> named) {
        var closure = new LinkedHashSet<String>();
        named.forEach(
                name -> {
                    closure.add(name);
                    closure.addAll(descendants(name));
                });
        return instantiable(closure);
    }

    /**
     * The types in {@code listed} an instance can actually carry, as a value-type list reads: the
     * classes themselves and nothing deriving from them, less the abstract ones. Official lists
     * name abstract classes too — the CGMES Terminal.ConductingEquipment list ends in {@code
     * cim:Equipment} — which admit nothing.
     */
    public Set<String> typedAs(Set<String> listed) {
        return instantiable(new LinkedHashSet<>(listed));
    }

    /**
     * Only a class the schema declares can be known to be abstract, and a schema that marks no
     * class concrete tells nothing either way — so when filtering leaves nothing, the unfiltered
     * set is the honest answer.
     */
    private Set<String> instantiable(Set<String> candidates) {
        var instantiable = new LinkedHashSet<String>();
        candidates.stream()
                .filter(name -> concrete.contains(name) || !classes.contains(name))
                .forEach(instantiable::add);
        return instantiable.isEmpty() ? candidates : instantiable;
    }

    /**
     * {@code named} without the classes another member already derives from, so a list naming a
     * range and each of its subclasses reads as the range.
     */
    public Set<String> roots(Set<String> named) {
        var roots = new LinkedHashSet<String>();
        named.stream()
                .filter(
                        name ->
                                named.stream()
                                        .noneMatch(
                                                other ->
                                                        !other.equals(name)
                                                                && isSubClassOf(name, other)))
                .forEach(roots::add);
        return roots;
    }

    private Set<String> descendants(String name) {
        var found = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(name);
        while (!queue.isEmpty()) {
            for (var sub : subClasses.getOrDefault(queue.poll(), Set.of())) {
                if (found.add(sub)) {
                    queue.add(sub);
                }
            }
        }
        return found;
    }
}
