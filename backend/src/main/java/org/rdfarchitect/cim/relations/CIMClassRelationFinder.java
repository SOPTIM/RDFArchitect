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

package org.rdfarchitect.cim.relations;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.cim.data.CIMObjectFetcher;
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.cim.relations.model.CIMClassUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * This class provides functionality to find classes referencing another.
 */
public class CIMClassRelationFinder {

    private static final String CLASSES_REFERENCING_VIA_ATTRIBUTES_QUERY = """
                PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT ?thatClass WHERE {
                    ?attribute  cims:dataType <thisClass> ;
                                rdfs:domain ?thatClass ;
                                a rdf:Property .
                    ?thatClass  a rdfs:Class .
                }
                """;

    private static final String CLASSES_REFERENCING_VIA_ASSOCIATIONS_QUERY = """
                PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                
                SELECT ?thatClass WHERE {
                    ?association    rdfs:range <thisClass> ;
                                    rdfs:domain ?thatClass ;
                                    cims:AssociationUsed "Yes" ;
                                    a rdf:Property .
                    ?thatClass      a rdfs:Class .
                }
                """;

    public CIMClassRelationFinder(Model ontology) {
        this.ontology = ontology;
        var prefixMapping = new PrefixMappingImpl();
        prefixMapping.setNsPrefixes(ontology.getNsPrefixMap());
        this.cimObjectFetcher = new CIMObjectFetcher(ontology.getGraph(), null, prefixMapping);
    }

    private final Model ontology;

    private final CIMObjectFetcher cimObjectFetcher;

    /**
     * Finds all classes referencing a given class URI via attributes or associations.
     *
     * @param graph     the graph containing the ontology
     * @param classUUID the UUID of the class
     * @return ClassRelationsDTO containing the classes referencing the given class URI
     */
    public static ClassRelationsDTO getAllClassRelations(Graph graph, UUID classUUID) {
        var ontology = ModelFactory.createModelForGraph(graph);
        var classURI = ontology.listSubjectsWithProperty(RDFA.uuid, ontology.createLiteral(classUUID.toString())).next().getURI();
        var cimClassRelationFinder = new CIMClassRelationFinder(ontology);
        var classReferencingThisClassVia = new HashMap<String, Collection<CIMClass>>();
        classReferencingThisClassVia.put("superClasses", cimClassRelationFinder.findSuperClasses(classUUID));
        classReferencingThisClassVia.put("attributes", cimClassRelationFinder.findClassesReferencingViaAttributes(classURI));
        classReferencingThisClassVia.put("associations", cimClassRelationFinder.findClassesReferencingViaAssociations(classURI));

        var cimClassRelationsDTO = new ClassRelationsDTO();
        cimClassRelationsDTO.setUuid(classUUID);
        cimClassRelationsDTO.setClassesReferencingThisClassFromCIM(classReferencingThisClassVia);
        return cimClassRelationsDTO;
    }

    /**
     * Finds all super classes of a given class uuid.
     *
     * @param classUUID the uuid of the class
     * @return a set of cim classes
     */
    public Set<CIMClass> findSuperClasses(UUID classUUID) {
        var classRessource = ontology.listSubjectsWithProperty(RDFA.uuid, ontology.createLiteral(classUUID.toString())).next();
        return CIMClassUtils.listSuperClasses(classRessource)
                .stream()
                .map(superClass -> cimObjectFetcher.fetchCIMClass(superClass.getProperty(RDFA.uuid).getObject().asLiteral().getString()))
                .collect(Collectors.toSet());
    }


    /**
     * Finds all classes referencing a given class URI via attributes.
     *
     * @param classUri the URI of the class
     * @return a list of classes referencing the given class URI via attributes
     */
    private List<CIMClass> findClassesReferencingViaAttributes(String classUri) {
        var query = CLASSES_REFERENCING_VIA_ATTRIBUTES_QUERY.replace("thisClass", classUri);
        return executeQueryAndParseToClassList(query, "thatClass");
    }

    /**
     * Finds all classes referencing a given class URI via associations.
     *
     * @param classUri the URI of the class
     * @return a list of classes referencing the given class URI via associations
     */
    private List<CIMClass> findClassesReferencingViaAssociations(String classUri) {
        var query = CLASSES_REFERENCING_VIA_ASSOCIATIONS_QUERY.replace("thisClass", classUri);
        return executeQueryAndParseToClassList(query, "thatClass");
    }

    private List<CIMClass> executeQueryAndParseToClassList(String query, String classUriVar) {
        try (var queryExecution = QueryExecutionFactory.create(query, ontology)) {
            var classUris = queryExecution.execSelect();
            var classList = new ArrayList<CIMClass>();
            while (classUris.hasNext()) {
                var classUri = classUris.next().getResource(classUriVar);
                classList.add(cimObjectFetcher.fetchCIMClass(classUri.getProperty(RDFA.uuid).getObject().asLiteral().getString()));
            }
            return classList;
        }
    }

}
