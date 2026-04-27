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

package org.rdfarchitect.shacl;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.rdfarchitect.shacl.property.PropertyGroupFactory;
import org.rdfarchitect.shacl.property.shapebuilder.NodeShapeBuilder;
import org.rdfarchitect.shacl.property.shapegenerator.CIMAssociationToHasTypePropertyShapeConverter;
import org.rdfarchitect.shacl.property.shapegenerator.CardinalityPropertyShapeFromCIMPropertyGenerator;
import org.rdfarchitect.shacl.property.shapegenerator.DatatypePropertyShapeFromCIMAttributeGenerator;
import org.rdfarchitect.shacl.property.shapegenerator.InverseCardinalityPropertyShapeFromCIMAssociationGenerator;
import org.rdfarchitect.shacl.property.shapegenerator.PropertyShapeFromCIMPropertyGenerator;
import org.rdfarchitect.shacl.property.shapegenerator.ValueTypePropertyShapeFromCIMAssociationGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SHACLFromCIMGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SHACLFromCIMGenerator.class);

    private final Model ontology;

    private final PrefixEntry shaclPrefix;

    private Model shacl;

    private final Map<Resource, Set<Resource>> propertyToPropertyShapesMapping;

    private final boolean closed;

    private final List<PropertyShapeFromCIMPropertyGenerator> propertyConverters =
            List.of(
                    new CardinalityPropertyShapeFromCIMPropertyGenerator(),
                    new ValueTypePropertyShapeFromCIMAssociationGenerator(),
                    new CIMAssociationToHasTypePropertyShapeConverter(),
                    new InverseCardinalityPropertyShapeFromCIMAssociationGenerator(),
                    new DatatypePropertyShapeFromCIMAttributeGenerator());

    public SHACLFromCIMGenerator(Model ontology, PrefixEntry shaclPrefix, boolean closed) {
        this.ontology = ontology;
        this.shaclPrefix = shaclPrefix;
        this.propertyToPropertyShapesMapping = new HashMap<>();
        this.closed = closed;
    }

    /**
     * Generates a SHACL model from the CIM ontology
     *
     * @return a SHACL model
     */
    public Model generate() {
        initResultModel();
        // create propertyShapes and nodeShapes
        for (var instantiableClass : listInstantiableClasses()) {
            var propertyShapes = new HashSet<Resource>();
            for (var property : CIMClassUtils.listAllProperties(instantiableClass)) {
                propertyShapes.addAll(createPropertyShape(property));
            }
            createNodeShape(instantiableClass, propertyShapes);
        }
        new PropertyGroupFactory(shacl, shaclPrefix)
                .createReferencedPropertyGroups()
                .forEach(propertyGroup -> shacl.add(propertyGroup.listProperties()));
        return shacl;
    }

    /**
     * Generates SHACL shapes for a specified class only
     *
     * @param classUUID the uuid of the class
     * @return a SHACL model
     */
    public Model generateForClassOnly(UUID classUUID) {
        var classResource =
                ontology.listSubjectsWithProperty(
                                RDFA.uuid, ontology.createLiteral(classUUID.toString()))
                        .next();
        initResultModel();
        var instantiableDerivingClasses =
                CIMClassUtils.findDerivingClasses(classResource).stream()
                        .filter(CIMClassUtils::isInstantiableClass)
                        .collect(Collectors.toSet());
        if (!CIMClassUtils.isInstantiableClass(classResource)
                && instantiableDerivingClasses.isEmpty()) {
            return shacl;
        }
        // create node shapes and property shapes
        var propertyShapes = new HashSet<Resource>();
        for (var property : CIMClassUtils.listAllProperties(classResource)) {
            propertyShapes.addAll(createPropertyShape(property));
        }
        if (CIMClassUtils.isInstantiableClass(classResource) && !propertyShapes.isEmpty()) {
            createNodeShape(classResource, propertyShapes);
        }
        new PropertyGroupFactory(shacl, shaclPrefix)
                .createReferencedPropertyGroups()
                .forEach(propertyGroup -> shacl.add(propertyGroup.listProperties()));
        return shacl;
    }

    private void initResultModel() {
        shacl = ModelFactory.createDefaultModel();
        shacl.setNsPrefixes(ontology);
        shacl.setNsPrefix(shaclPrefix.getPrefix(), shaclPrefix.getUri());
        shacl.setNsPrefix("sh", SHACL.NS);
    }

    /**
     * List all classes that can be instantiated
     *
     * @return a set of resources
     */
    private Set<Resource> listInstantiableClasses() {
        return ontology.listSubjectsWithProperty(RDF.type, RDFS.Class).toSet().stream()
                .filter(CIMClassUtils::isInstantiableClass)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a node shape and the required property shapes for a specified class.
     *
     * @param classResource the class
     */
    private void createNodeShape(Resource classResource, Set<Resource> propertyShapes) {
        // create node shapes
        var nodeShape =
                new NodeShapeBuilder(shacl)
                        .setClosed(closed)
                        .setPrefixEntry(shaclPrefix)
                        .setTargetClassUri(classResource.getURI())
                        .setPropertyShapes(propertyShapes)
                        .build();
        // add to shacl graph
        shacl.add(nodeShape.listProperties());
    }

    /**
     * Creates property shapes for a specified property.
     *
     * @param property the property
     * @return a set of property shapes
     */
    private Collection<Resource> createPropertyShape(Resource property) {
        // early return if property shapes for this property are already created
        if (propertyToPropertyShapesMapping.containsKey(property)) {
            return propertyToPropertyShapesMapping.get(property);
        }
        var propertyShapes = new HashSet<Resource>();
        for (var converter : propertyConverters) {
            try {
                var propertyShape =
                        converter
                                .setOntologyModel(ontology)
                                .setShaclModel(shacl)
                                .setShaclPrefix(shaclPrefix)
                                .createPropertyShape(property);
                if (propertyShape != null) {
                    // create property shapes
                    shacl.add(propertyShape.listProperties());
                    propertyShapes.add(propertyShape);
                }
            } catch (Exception e) {
                logger.warn(
                        "Error creating property shape for property {} with converter {}: {}",
                        property.getURI(),
                        converter.getClass().getSimpleName(),
                        e.getMessage());
                shacl.add(
                        shacl.createResource(RDFA.URI + "Errors"),
                        RDFS.comment,
                        String.format(
                                "Error creating property shape for property %s with converter %s: %s",
                                property.getURI(),
                                converter.getClass().getSimpleName(),
                                e.getMessage()));
            }
        }
        // add property shapes to ontology
        propertyShapes.forEach(shape -> shacl.add(shape.listProperties()));
        // add to map to avoid duplicates
        propertyToPropertyShapesMapping.put(property, new HashSet<>(propertyShapes));
        return propertyShapes;
    }
}
