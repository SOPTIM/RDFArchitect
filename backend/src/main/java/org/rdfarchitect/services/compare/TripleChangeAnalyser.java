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

package org.rdfarchitect.services.compare;

import lombok.experimental.UtilityClass;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.changes.triplechanges.TripleClassChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePropertyChange;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class TripleChangeAnalyser {

    public List<TriplePackageChange> compareGraphs(Graph originalGraph, Graph updatedGraph) {
        var result = new ArrayList<TriplePackageChange>();

        var packages = getPackages(originalGraph, updatedGraph);
        for (var packageURI : packages) {
            var packageChange = comparePackage(originalGraph, updatedGraph, packageURI);
            if (packageChange != null) {
                result.add(packageChange);
            }
        }

        var defaultPackageChange = compareDefaultPackage(originalGraph, updatedGraph);
        if (defaultPackageChange != null) {
            result.add(defaultPackageChange);
        }

        return result;
    }

    public List<TripleClassChange> compareGraphsDisregardingPackages(Graph originalGraph, Graph updatedGraph) {
        var classChanges = new ArrayList<TripleClassChange>();
        var classes = getAllClasses(originalGraph, updatedGraph);
        for (var classURI : classes) {
            var classChange = compareClass(originalGraph, updatedGraph, classURI);
            if (classChange != null) {
                classChanges.add(classChange);
            }
        }
        return classChanges;
    }

    /**
     * Compares a package in the original graph with the same package in the updated graph.
     * If the package is not defined in either graph, it is considered external.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param packageURI    The URI of the package to compare.
     *
     * @return A TriplePackageChange object representing the differences in the package, or null if there are no changes.
     */
    private TriplePackageChange comparePackage(Graph originalGraph, Graph updatedGraph, String packageURI) {
        var label = packageURI.split("#")[1];
        var packageChange = TriplePackageChange.builder()
                                               .uri(packageURI)
                                               .label(label)
                                               .build();
        if (!originalGraph.contains(NodeFactory.createURI(packageURI), RDF.type.asNode(), CIMS.classCategory.asNode())
                  && !updatedGraph.contains(NodeFactory.createURI(packageURI), RDF.type.asNode(), CIMS.classCategory.asNode())) {
            packageChange.setExternal(true);
        }

        var packageChanges = compareResource(originalGraph, updatedGraph, packageURI);
        if (!packageChanges.isEmpty()) {
            packageChange.setChanges(packageChanges);
        }

        var classChanges = new ArrayList<TripleClassChange>();
        var classes = getClassesInPackage(originalGraph, updatedGraph, packageURI);
        for (var classURI : classes) {
            var classChange = compareClass(originalGraph, updatedGraph, classURI);
            if (classChange != null) {
                classChanges.add(classChange);
            }
        }
        packageChange.setClasses(classChanges);

        if (packageChange.getChanges() == null && packageChange.getClasses().isEmpty()) {
            return null;
        }
        return packageChange;
    }

    /**
     * Compares all classes without a package and groups them in a default package.
     * Since the default package is not defined in the graph, it can have no changes in the package definition.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     *
     * @return A TriplePackageChange object representing the differences between the classes, or null if there are no changes.
     */
    private TriplePackageChange compareDefaultPackage(Graph originalGraph, Graph updatedGraph) {
        var packageChange = TriplePackageChange.builder()
                                               .label("default")
                                               .build();

        var classChanges = new ArrayList<TripleClassChange>();
        var classes = getClassesWithoutPackage(originalGraph, updatedGraph);
        for (var classURI : classes) {
            var classChange = compareClass(originalGraph, updatedGraph, classURI);
            if (classChange != null) {
                classChanges.add(classChange);
            }
        }
        packageChange.setClasses(classChanges);

        if (packageChange.getClasses().isEmpty()) {
            return null;
        }
        return packageChange;
    }

    /**
     * Compares a class in the original graph with the same class in the updated graph.
     * It compares the properties, attributes, associations, and enum entries of the class.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A ClassChange object representing the differences in the class, or null if there are no changes.
     */
    private TripleClassChange compareClass(Graph originalGraph, Graph updatedGraph, String classURI) {
        var label = classURI.split("#")[1];
        var classChange = TripleClassChange.builder()
                                           .label(label)
                                           .uri(classURI)
                                           .build();

        var classChanges = compareResource(originalGraph, updatedGraph, classURI);
        if (!classChanges.isEmpty()) {
            classChange.setChanges(classChanges);
        }

        var attributeChanges = new ArrayList<>(compareAttributes(originalGraph, updatedGraph, classURI));
        if (!attributeChanges.isEmpty()) {
            classChange.setAttributes(attributeChanges);
        }
        var associationsChanges = new ArrayList<>(compareAssociations(originalGraph, updatedGraph, classURI));
        if (!associationsChanges.isEmpty()) {
            classChange.setAssociations(associationsChanges);
        }
        var enumEntriesChanges = new ArrayList<>(compareEnumEntries(originalGraph, updatedGraph, classURI));
        if (!enumEntriesChanges.isEmpty()) {
            classChange.setEnumEntries(enumEntriesChanges);
        }

        if (classChange.getChanges() == null && classChange.getAttributes() == null && classChange.getAssociations() == null && classChange.getEnumEntries() == null) {
            return null;
        }
        return classChange;
    }

    /**
     * Compares the properties of a resource in the original graph with the properties of the same resource in the updated graph.
     * Triples with the predicate RDFA.uuid are ignored, as they are originally only present in the InMemoryGraph.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param uri           The URI of the resource to compare.
     *
     * @return A map of property URIs to TriplePropertyChange objects representing the differences in properties.
     */
    private List<TriplePropertyChange> compareResource(Graph originalGraph, Graph updatedGraph, String uri) {
        var originalTriples = originalGraph.find(NodeFactory.createURI(uri), Node.ANY, Node.ANY).toList();
        var updatedTriples = updatedGraph.find(NodeFactory.createURI(uri), Node.ANY, Node.ANY).toList();
        var propertyChanges = new ArrayList<TriplePropertyChange>();

        var originalValues = originalTriples.stream()
                                            .filter(triple -> !triple.getPredicate().equals(CIMS.stereotype.asNode()))
                                            .collect(Collectors.toMap(Triple::getPredicate, Triple::getObject, (a, b) -> a));

        var updatedValues = updatedTriples.stream()
                                          .filter(triple -> !triple.getPredicate().equals(CIMS.stereotype.asNode()))
                                          .collect(Collectors.toMap(Triple::getPredicate, Triple::getObject, (a, b) -> a));

        Set<Node> allPredicates = new HashSet<>();
        allPredicates.addAll(originalValues.keySet());
        allPredicates.addAll(updatedValues.keySet());

        for (Node predicate : allPredicates) {
            // ignore uuid predicates, stereotypes are handled separately
            if (predicate.equals(RDFA.uuid.asNode()) || predicate.equals(CIMS.stereotype.asNode())) {
                continue;
            }

            var from = originalValues.get(predicate);
            var to = updatedValues.get(predicate);

            if (!Objects.equals(from, to)) {
                var change = new TriplePropertyChange();
                change.setPredicate(predicate.toString());
                change.setFrom(from != null ? from.toString() : null);
                change.setTo(to != null ? to.toString() : null);
                propertyChanges.add(change);
            }
        }

        // handle stereotypes separately
        var originalStereotypes = originalTriples.stream()
                                                 .filter(triple -> triple.getPredicate().equals(CIMS.stereotype.asNode()))
                                                 .map(triple -> triple.getObject().toString())
                                                 .collect(Collectors.toSet());
        var updatedStereotypes = updatedTriples.stream()
                                               .filter(triple -> triple.getPredicate().equals(CIMS.stereotype.asNode()))
                                               .map(triple -> triple.getObject().toString())
                                               .collect(Collectors.toSet());

        for (var originalStereotype : originalStereotypes) {
            if (!updatedStereotypes.contains(originalStereotype)) {
                var change = new TriplePropertyChange();
                change.setPredicate(CIMS.stereotype.toString());
                change.setFrom(originalStereotype);
                propertyChanges.add(change);
            } else {
                updatedStereotypes.remove(originalStereotype);
            }
        }

        for (var updatedStereotype : updatedStereotypes) {
            var change = new TriplePropertyChange();
            change.setPredicate(CIMS.stereotype.toString());
            change.setTo(updatedStereotype);
            propertyChanges.add(change);
        }

        return propertyChanges;
    }

    /**
     * Compares the attributes, associations, and enum entries of a class in the original graph with the same class in the updated graph.
     * The prefix and class name are excluded from the label of the NestedObjectChange objects.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in attributes, associations, or enum entries.
     */
    private List<TripleResourceChange> compareAttributes(Graph originalGraph, Graph updatedGraph, String classURI) {
        var attributesChanges = new ArrayList<TripleResourceChange>();
        var attributes = getAttributes(originalGraph, updatedGraph, classURI);

        for (String attributeURI : attributes) {
            var nestedChange = new TripleResourceChange();
            nestedChange.setUri(attributeURI);
            nestedChange.setLabel(attributeURI.split("#")[1].split("\\.", 2)[1]);

            var attrChanges = compareResource(originalGraph, updatedGraph, attributeURI);
            if (!attrChanges.isEmpty()) {
                nestedChange.setChanges(attrChanges);
                attributesChanges.add(nestedChange);
            }
        }

        return attributesChanges;
    }

    /**
     * Compares the associations of a class in the original graph with the associations of the same class in the updated graph.
     * The prefix is excluded from the label of the NestObjectChange objects.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in associations.
     */
    private List<TripleResourceChange> compareAssociations(Graph originalGraph, Graph updatedGraph, String classURI) {
        var associationsChanges = new ArrayList<TripleResourceChange>();
        var associations = getAssociations(originalGraph, updatedGraph, classURI);

        for (String associationURI : associations) {
            TripleResourceChange nestedChange = new TripleResourceChange();
            nestedChange.setUri(associationURI);
            nestedChange.setLabel(associationURI.split("#")[1]);

            var assocChanges = compareResource(originalGraph, updatedGraph, associationURI);
            if (!assocChanges.isEmpty()) {
                nestedChange.setChanges(assocChanges);
                associationsChanges.add(nestedChange);
            }
        }

        return associationsChanges;
    }

    /**
     * Compares the enum entries of a class in the original graph with the enum entries of the same class in the updatedgraph.
     * The prefix and class name are excluded from the label of the NestedObjectChange objects.
     *
     * @param originalGraph The original version to compare to.
     * @param updatedGraph  The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in enum entries.
     */
    private List<TripleResourceChange> compareEnumEntries(Graph originalGraph, Graph updatedGraph, String classURI) {
        var enumEntriesChanges = new ArrayList<TripleResourceChange>();

        var enumEntries = getEnumEntries(originalGraph, updatedGraph, classURI);
        for (String enumEntryURI : enumEntries) {
            var nestedChange = new TripleResourceChange();
            nestedChange.setUri(enumEntryURI);
            nestedChange.setLabel(enumEntryURI.split("#")[1].split("\\.", 2)[1]);

            var enumEntryChanges = compareResource(originalGraph, updatedGraph, enumEntryURI);
            if (!enumEntryChanges.isEmpty()) {
                nestedChange.setChanges(enumEntryChanges);
                enumEntriesChanges.add(nestedChange);
            }
        }

        return enumEntriesChanges;
    }

    /**
     * Retrieves all packages present in either of the two input graphs.
     * External packages are also retrieved by their usage as a classCategory in a class.
     *
     * @param graph1 The first graph to search for packages.
     * @param graph2 The second graph to search for packages.
     *
     * @return A set of package URIs.
     */
    private HashSet<String> getPackages(Graph graph1, Graph graph2) {
        var packages = new HashSet<String>();
        graph1.find(Node.ANY, RDF.type.asNode(), CIMS.classCategory.asNode()).forEachRemaining(triple -> packages.add(triple.getSubject().toString()));
        graph2.find(Node.ANY, RDF.type.asNode(), CIMS.classCategory.asNode()).forEachRemaining(triple -> packages.add(triple.getSubject().toString()));
        graph1.find(Node.ANY, CIMS.belongsToCategory.asNode(), Node.ANY).forEachRemaining(triple -> packages.add(triple.getObject().toString()));
        graph2.find(Node.ANY, CIMS.belongsToCategory.asNode(), Node.ANY).forEachRemaining(triple -> packages.add(triple.getObject().toString()));
        return packages;
    }

    private HashSet<String> getAllClasses(Graph graph1, Graph graph2) {
        var classes = new HashSet<String>();
        graph1.find(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode()).forEachRemaining(triple -> classes.add(triple.getSubject().toString()));
        graph2.find(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode()).forEachRemaining(triple -> classes.add(triple.getSubject().toString()));
        return classes;
    }

    /**
     * Retrieves all classes in a specific package from either of the two input graphs.
     *
     * @param graph1     The first graph to search for classes.
     * @param graph2     The second graph to search for classes.
     * @param packageURI The URI of the package to search for classes.
     *
     * @return A set of class URIs.
     */
    private HashSet<String> getClassesInPackage(Graph graph1, Graph graph2, String packageURI) {
        var classes = new HashSet<String>();
        graph1.find(Node.ANY, CIMS.belongsToCategory.asNode(), NodeFactory.createURI(packageURI)).forEachRemaining(triple -> classes.add(triple.getSubject().toString()));
        graph2.find(Node.ANY, CIMS.belongsToCategory.asNode(), NodeFactory.createURI(packageURI)).forEachRemaining(triple -> classes.add(triple.getSubject().toString()));
        return classes;
    }

    /**
     * Retrieves all classes without a package from either of the two input graphs.
     * Classes without a package are identified by the absence of the belongsToCategory predicate.
     *
     * @param graph1 The first graph to search for classes.
     * @param graph2 The second graph to search for classes.
     *
     * @return A set of class URIs that do not belong to any package.
     */
    private HashSet<String> getClassesWithoutPackage(Graph graph1, Graph graph2) {
        var classes = new HashSet<String>();
        graph1.find(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode()).forEachRemaining(triple -> {
            if (!graph1.contains(triple.getSubject(), CIMS.belongsToCategory.asNode(), Node.ANY)) {
                classes.add(triple.getSubject().toString());
            }
        });
        graph2.find(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode()).forEachRemaining(triple -> {
            if (!graph2.contains(triple.getSubject(), CIMS.belongsToCategory.asNode(), Node.ANY)) {
                classes.add(triple.getSubject().toString());
            }
        });
        return classes;
    }

    /**
     * Retrieves all attributes of a class from either of the two input graphs.
     *
     * @param graph1   The first graph to search for attributes.
     * @param graph2   The second graph to search for attributes.
     * @param classURI The URI of the class to search for attributes.
     *
     * @return A set of attribute URIs associated with the class.
     */
    private HashSet<String> getAttributes(Graph graph1, Graph graph2, String classURI) {
        var attributes = new HashSet<String>();

        graph1.find(Node.ANY, RDFS.domain.asNode(), NodeFactory.createURI(classURI)).forEachRemaining(triple -> {
            if (graph1.contains(triple.getSubject(), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())) {
                attributes.add(triple.getSubject().toString());
            }
        });
        graph2.find(Node.ANY, RDFS.domain.asNode(), NodeFactory.createURI(classURI)).forEachRemaining(triple -> {
            if (graph2.contains(triple.getSubject(), CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode())) {
                attributes.add(triple.getSubject().toString());
            }
        });

        return attributes;
    }

    /**
     * Retrieves all associations of a class from either of the two input graphs.
     * Fetches only one direction of the association, as the other direction is compared in the other class.
     *
     * @param graph1   The first graph to search for associations.
     * @param graph2   The second graph to search for associations.
     * @param classURI The URI of the class to search for associations.
     *
     * @return A set of association URIs associated with the class.
     */
    private HashSet<String> getAssociations(Graph graph1, Graph graph2, String classURI) {
        var associations = new HashSet<String>();
        graph1.find(Node.ANY, CIMS.associationUsed.asNode(), Node.ANY).forEachRemaining(triple -> {
            if (graph1.contains(triple.getSubject(), RDFS.domain.asNode(), NodeFactory.createURI(classURI))) {
                associations.add(triple.getSubject().toString());
            }
        });
        graph2.find(Node.ANY, CIMS.associationUsed.asNode(), Node.ANY).forEachRemaining(triple -> {
            if (graph2.contains(triple.getSubject(), RDFS.domain.asNode(), NodeFactory.createURI(classURI))) {
                associations.add(triple.getSubject().toString());
            }
        });
        return associations;
    }

    /**
     * Retrieves all enum entries belonging to a class from either of the two input graphs.
     *
     * @param graph1   The first graph to search for enum entries.
     * @param graph2   The second graph to search for enum entries.
     * @param classURI The URI of the class to search for enum entries.
     *
     * @return A set of enum entry URIs associated with the class.
     */
    private HashSet<String> getEnumEntries(Graph graph1, Graph graph2, String classURI) {
        var enumEntries = new HashSet<String>();
        graph1.find(Node.ANY, RDF.type.asNode(), NodeFactory.createURI(classURI)).forEachRemaining(triple -> enumEntries.add(triple.getSubject().toString()));
        graph2.find(Node.ANY, RDF.type.asNode(), NodeFactory.createURI(classURI)).forEachRemaining(triple -> enumEntries.add(triple.getSubject().toString()));
        return enumEntries;
    }
}
