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

package org.rdfarchitect.models.cim.ontology;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.exception.database.QueryException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OntologyFactory {

    private static final String ONTOLOGY_IRI = "?ontologyIRI";
    private static final String ONTOLOGY_FIELD_IRI = "?ontologyFieldIRI";
    private static final String ONTOLOGY_FIELD_VALUE = "?OntologyFieldValue";

    private static final String QUERY = """
              PREFIX owl: <http://www.w3.org/2002/07/owl#>
              PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              
              SELECT {ONTOLOGY_IRI} {ONTOLOGY_FIELD_IRI} {ONTOLOGY_FIELD_VALUE} WHERE {
                  {ONTOLOGY_IRI}    rdf:type owl:Ontology;
                                  {ONTOLOGY_FIELD_IRI} {ONTOLOGY_FIELD_VALUE}.
              }ORDER BY(STR({ONTOLOGY_FIELD_IRI}))
              """
              .replace("{ONTOLOGY_IRI}", ONTOLOGY_IRI)
              .replace("{ONTOLOGY_FIELD_IRI}", ONTOLOGY_FIELD_IRI)
              .replace("{ONTOLOGY_FIELD_VALUE}", ONTOLOGY_FIELD_VALUE);

    public static OntologyDTO createOntologyDTO(Model model) {
        var query = QueryFactory.create(QUERY);
        try (var qexec = QueryExecutionFactory.create(query, model)) {
            var results = qexec.execSelect();
            return create(results);
        } catch (Exception e) {
            throw new QueryException(e.toString());
        }
    }

    private static OntologyDTO create(ResultSet results) {
        if (!results.hasNext()) {
            return null;
        }
        var ontologyDTO = new OntologyDTO();
        var entries = ontologyDTO.getEntries();

        while (results.hasNext()) {
            var result = results.next();

            var ontologyFieldIRI = result.get(ONTOLOGY_FIELD_IRI).asResource().getURI();

            // handle case rdfa:uuid to set uuid
            if (ontologyFieldIRI.equals(RDFA.uuid.getURI())) {
                var ontologyUUIDLiteral = result.get(ONTOLOGY_FIELD_VALUE).asLiteral();
                ontologyDTO.setUuid(ontologyUUIDLiteral.getString());
            }
            // handle case rdf:type owl:Ontology to set namespace and label
            else if (ontologyFieldIRI.equals(RDF.type.getURI())) {
                var ontologyIRIResource = result.get(ONTOLOGY_IRI).asResource();
                ontologyDTO.setNamespace(ontologyIRIResource.getNameSpace());
            } else {
                entries.add(createOntologyEntry(result));
            }
        }
        return ontologyDTO;
    }

    private static OntologyEntry createOntologyEntry(QuerySolution qs) {
        var ontologyFieldIRI = qs.get(ONTOLOGY_FIELD_IRI).asResource();
        var ontologyFieldValueNode = qs.get(ONTOLOGY_FIELD_VALUE);
        var ontologyEntry = new OntologyEntry().setIri(ontologyFieldIRI.getURI());

        if (ontologyFieldValueNode.isLiteral()) {
            var ontologyFieldValueLiteral = ontologyFieldValueNode.asLiteral();
            ontologyEntry.setIriEntry(false)
                         .setValue(ontologyFieldValueLiteral.getString());
            //if the datatype is equal to rdf:langString, the literal is language tagged, so we do not set the dataTypeIRI
            if (!ontologyFieldValueLiteral.getDatatypeURI().equals(RDF.langString.getURI())) {
                ontologyEntry.setDatatypeIri(ontologyFieldValueLiteral.getDatatypeURI());
            }
        } else if (ontologyFieldValueNode.isResource()) {
            var ontologyFieldValueResource = ontologyFieldValueNode.asResource();
            ontologyEntry.setIriEntry(true)
                         .setValue(ontologyFieldValueResource.getURI());
        } else {
            throw new QueryException("Unknown node type for ontology field value: " + ontologyFieldValueNode);
        }
        return ontologyEntry;
    }
}
