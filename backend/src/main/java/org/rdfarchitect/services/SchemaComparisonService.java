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

package org.rdfarchitect.services;

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.TxnType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.cim.changes.ClassChange;
import org.rdfarchitect.cim.changes.NestedObjectChange;
import org.rdfarchitect.cim.changes.PackageChange;
import org.rdfarchitect.cim.changes.PropertyChange;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaComparisonService implements SchemaComparisonUseCase {

    private static final String GRAPH_URI = "http://example.org/graph";
    private final DatabasePort databasePort;

    @Override
    public List<PackageChange> compareSchemas(GraphIdentifier graphIdentifier, MultipartFile file) {
        var currentGraph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        var uploadedGraph = new GraphFileSourceBuilderImpl()
                  .setFile(file)
                  .setGraphName(GRAPH_URI)
                  .build()
                  .graph();
        var result = new ArrayList<PackageChange>();

        try {
            currentGraph.begin(TxnType.READ);
            result = compareGraphs(currentGraph, uploadedGraph);
        } finally {
            currentGraph.end();
        }
        return result;
    }

    @Override
    public List<PackageChange> compareSchemas(MultipartFile file1, MultipartFile file2) {
        var graph1 = new GraphFileSourceBuilderImpl()
                  .setFile(file1)
                  .setGraphName(GRAPH_URI + "/file1")
                  .build()
                  .graph();
        var graph2 = new GraphFileSourceBuilderImpl()
                  .setFile(file2)
                  .setGraphName(GRAPH_URI + "/file2")
                  .build()
                  .graph();

        return compareGraphs(graph1, graph2);
    }

    @Override
    public List<PackageChange> compareSchemas(GraphIdentifier graphIdentifier, GraphIdentifier otherGraphIdentifier) {
        var graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
        var otherGraph = databasePort.getGraphWithContext(otherGraphIdentifier).getRdfGraph();
        var result = new ArrayList<PackageChange>();

        if (graphIdentifier.equals(otherGraphIdentifier)) {
            return result;
        }
        try {
            graph.begin(TxnType.READ);
            try {
                otherGraph.begin(TxnType.READ);
                result = compareGraphs(graph, otherGraph);
            } finally {
                otherGraph.end();
            }
        } finally {
            graph.end();
        }
        return result;
    }

    private ArrayList<PackageChange> compareGraphs(Graph currentGraph, Graph updatedGraph) {
        var result = new ArrayList<PackageChange>();

        var packages = getPackages(currentGraph, updatedGraph);
        for (var packageURI : packages) {
            var packageChange = comparePackage(currentGraph, updatedGraph, packageURI);
            if (packageChange != null) {
                result.add(packageChange);
            }
        }

        var defaultPackageChange = compareDefaultPackage(currentGraph, updatedGraph);
        if (defaultPackageChange != null) {
            result.add(defaultPackageChange);
        }

        return result;
    }

    /**
     * Compares a package in the current graph with the same package in the updated graph.
     * If the package is not defined in either graph, it is considered external.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param packageURI    The URI of the package to compare.
     *
     * @return A PackageChange object representing the differences in the package, or null if there are no changes.
     */
    private PackageChange comparePackage(Graph currentGraph, Graph updatedGraph, String packageURI) {
        var packageChange = PackageChange.builder()
                                         .label(packageURI)
                                         .build();
        if (!currentGraph.contains(NodeFactory.createURI(packageURI), RDF.type.asNode(), CIMS.classCategory.asNode())
                  && !updatedGraph.contains(NodeFactory.createURI(packageURI), RDF.type.asNode(), CIMS.classCategory.asNode())) {
            packageChange.setExternal(true);
        }

        var packageChanges = compareResource(currentGraph, updatedGraph, packageURI);
        if (!packageChanges.isEmpty()) {
            packageChange.setChanges(packageChanges);
        }

        var classChanges = new ArrayList<ClassChange>();
        var classes = getClassesInPackage(currentGraph, updatedGraph, packageURI);
        for (var classURI : classes) {
            var classChange = compareClass(currentGraph, updatedGraph, classURI);
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
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     *
     * @return A PackageChange object representing the differences between the classes, or null if there are no changes.
     */
    private PackageChange compareDefaultPackage(Graph currentGraph, Graph updatedGraph) {
        var packageChange = PackageChange.builder()
                                         .label("default")
                                         .build();

        var classChanges = new ArrayList<ClassChange>();
        var classes = getClassesWithoutPackage(currentGraph, updatedGraph);
        for (var classURI : classes) {
            var classChange = compareClass(currentGraph, updatedGraph, classURI);
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
     * Compares a class in the current graph with the same class in the updated graph.
     * It compares the properties, attributes, associations, and enum entries of the class.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A ClassChange object representing the differences in the class, or null if there are no changes.
     */
    private ClassChange compareClass(Graph currentGraph, Graph updatedGraph, String classURI) {
        var classChange = ClassChange.builder()
                                     .label(classURI)
                                     .build();

        var classChanges = compareResource(currentGraph, updatedGraph, classURI);
        if (!classChanges.isEmpty()) {
            classChange.setChanges(classChanges);
        }

        List<NestedObjectChange> attributeChanges = new ArrayList<>(compareAttributes(currentGraph, updatedGraph, classURI));
        if (!attributeChanges.isEmpty()) {
            classChange.setAttributes(attributeChanges);
        }
        List<NestedObjectChange> associationsChanges = new ArrayList<>(compareAssociations(currentGraph, updatedGraph, classURI));
        if (!associationsChanges.isEmpty()) {
            classChange.setAssociations(associationsChanges);
        }
        List<NestedObjectChange> enumEntriesChanges = new ArrayList<>(compareEnumEntries(currentGraph, updatedGraph, classURI));
        if (!enumEntriesChanges.isEmpty()) {
            classChange.setEnumEntries(enumEntriesChanges);
        }

        if (classChange.getChanges() == null && classChange.getAttributes() == null && classChange.getAssociations() == null && classChange.getEnumEntries() == null) {
            return null;
        }
        return classChange;
    }

    /**
     * Compares the properties of a resource in the current graph with the properties of the same resource in the updated graph.
     * Triples with the predicate RDFA.uuid are ignored, as they are currently only present in the InMemoryGraph.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param uri           The URI of the resource to compare.
     *
     * @return A map of property URIs to PropertyChange objects representing the differences in properties.
     */
    private Map<String, PropertyChange> compareResource(Graph currentGraph, Graph updatedGraph, String uri) {
        var currentTriples = currentGraph.find(NodeFactory.createURI(uri), Node.ANY, Node.ANY).toList();
        var updatedTriples = updatedGraph.find(NodeFactory.createURI(uri), Node.ANY, Node.ANY).toList();
        var propertyChanges = new HashMap<String, PropertyChange>();

        Map<Node, Node> currentValues = currentTriples.stream()
                                                      .collect(Collectors.toMap(Triple::getPredicate, Triple::getObject, (a, b) -> a));

        Map<Node, Node> updatedValues = updatedTriples.stream()
                                                        .collect(Collectors.toMap(Triple::getPredicate, Triple::getObject, (a, b) -> a));

        Set<Node> allPredicates = new HashSet<>();
        allPredicates.addAll(currentValues.keySet());
        allPredicates.addAll(updatedValues.keySet());

        for (Node predicate : allPredicates) {
            if (predicate == RDFA.uuid.asNode()) {
                continue;
            }

            Node from = currentValues.get(predicate);
            Node to = updatedValues.get(predicate);

            if (!Objects.equals(from, to)) {
                PropertyChange change = new PropertyChange();
                change.setFrom(from != null ? from.toString() : null);
                change.setTo(to != null ? to.toString() : null);
                propertyChanges.put(predicate.getURI(), change);
            }
        }

        return propertyChanges;
    }

    /**
     * Compares the attributes, associations, and enum entries of a class in the current graph with the same class in the updated graph.
     * The prefix and class name are excluded from the label of the NestedObjectChange objects.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in attributes, associations, or enum entries.
     */
    private List<NestedObjectChange> compareAttributes(Graph currentGraph, Graph updatedGraph, String classURI) {
        List<NestedObjectChange> attributesChanges = new ArrayList<>();

        HashSet<String> attributes = getAttributes(currentGraph, updatedGraph, classURI);

        for (String attributeURI : attributes) {
            NestedObjectChange nestedChange = new NestedObjectChange();
            nestedChange.setLabel(attributeURI.split("#")[1].split("\\.", 2)[1]);

            Map<String, PropertyChange> attrChanges = compareResource(currentGraph, updatedGraph, attributeURI);
            if (!attrChanges.isEmpty()) {
                nestedChange.setChanges(attrChanges);
                attributesChanges.add(nestedChange);
            }
        }

        return attributesChanges;
    }

    /**
     * Compares the associations of a class in the current graph with the associations of the same class in the updated graph.
     * The prefix is excluded from the label of the NestObjectChange objects.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in associations.
     */
    private List<NestedObjectChange> compareAssociations(Graph currentGraph, Graph updatedGraph, String classURI) {
        List<NestedObjectChange> associationsChanges = new ArrayList<>();

        HashSet<String> associations = getAssociations(currentGraph, updatedGraph, classURI);

        for (String associationURI : associations) {
            NestedObjectChange nestedChange = new NestedObjectChange();
            nestedChange.setLabel(associationURI.split("#")[1]);

            Map<String, PropertyChange> assocChanges = compareResource(currentGraph, updatedGraph, associationURI);
            if (!assocChanges.isEmpty()) {
                nestedChange.setChanges(assocChanges);
                associationsChanges.add(nestedChange);
            }
        }

        return associationsChanges;
    }

    /**
     * Compares the enum entries of a class in the current graph with the enum entries of the same class in the updatedgraph.
     * The prefix and class name are excluded from the label of the NestedObjectChange objects.
     *
     * @param currentGraph  The original version to compare to.
     * @param updatedGraph The Graph which is compared to the original graph.
     * @param classURI      The URI of the class to compare.
     *
     * @return A list of NestedObjectChange objects representing the differences in enum entries.
     */
    private List<NestedObjectChange> compareEnumEntries(Graph currentGraph, Graph updatedGraph, String classURI) {
        List<NestedObjectChange> enumEntriesChanges = new ArrayList<>();

        var enumEntries = getEnumEntries(currentGraph, updatedGraph, classURI);
        for (String enumEntryURI : enumEntries) {
            var nestedChange = new NestedObjectChange();
            nestedChange.setLabel(enumEntryURI.split("#")[1].split("\\.", 2)[1]);

            Map<String, PropertyChange> enumEntryChanges = compareResource(currentGraph, updatedGraph, enumEntryURI);
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
        graph1.find(Node.ANY, CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode()).forEachRemaining(triple -> {
            if (triple.getSubject().toString().contains(classURI)) {
                attributes.add(triple.getSubject().toString());
            }
        });
        graph2.find(Node.ANY, CIMS.stereotype.asNode(), CIMStereotypes.attribute.asNode()).forEachRemaining(triple -> {
            if (triple.getSubject().toString().contains(classURI)) {
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
            if (triple.getSubject().toString().contains(classURI)) {
                associations.add(triple.getSubject().toString());
            }
        });
        graph2.find(Node.ANY, CIMS.associationUsed.asNode(), Node.ANY).forEachRemaining(triple -> {
            if (triple.getSubject().toString().contains(classURI)) {
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
