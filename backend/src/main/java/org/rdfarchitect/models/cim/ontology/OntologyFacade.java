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

import lombok.RequiredArgsConstructor;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateException;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.Objects;

@RequiredArgsConstructor
public class OntologyFacade {

    private static final String INSERT_UPDATE = """
              {PREFIXES}
              INSERT DATA {
                {ONTOLOGY_IRI} a <http://www.w3.org/2002/07/owl#Ontology> .
                {TRIPLES}
              }
              """;

    private static final String DELETE_UPDATE = """
              DELETE {
                ?ontologyIRI a <http://www.w3.org/2002/07/owl#Ontology> .
                ?ontologyIRI ?ontologyProperty ?ontologyValue .
              }
              WHERE {
                ?ontologyIRI a <http://www.w3.org/2002/07/owl#Ontology> .
                OPTIONAL {
                  ?ontologyIRI ?ontologyProperty ?ontologyValue .
                }
              }
              """;

    private static final String REPLACE_UPDATE = """
              {PREFIXES}
              DELETE {
                ?ontologyIRI    a <http://www.w3.org/2002/07/owl#Ontology> ;
                                ?ontologyProperty ?ontologyValue .
              }
              INSERT {
                {ONTOLOGY_IRI} a <http://www.w3.org/2002/07/owl#Ontology> .
                {TRIPLES}
              }
              WHERE {
                  ?ontologyIRI a <http://www.w3.org/2002/07/owl#Ontology> .
                  OPTIONAL {
                    ?ontologyIRI ?ontologyProperty ?ontologyValue .
                  }
              }
              """;

    private final Model model;

    public void createOntology(OntologyDTO newOntologyDTO) {
        var prefixString = buildPrefixString();

        var ontologyIRI = newOntologyDTO.getNamespace() + "Ontology";
        var dctLanguageTag = getDCTLanguageTag(newOntologyDTO);
        // Add UUID triple
        var triplesToInsert = new StringBuilder()
                  .append("<")
                  .append(ontologyIRI)
                  .append("> <")
                  .append(RDFA.uuid.getURI())
                  .append("> \"")
                  .append(newOntologyDTO.getUuid())
                  .append("\" .\n");
        for (var entry : newOntologyDTO.getEntries()) {
            triplesToInsert.append(ontologyEntryToString(ontologyIRI, entry, dctLanguageTag));
        }

        var insertUpdate = UpdateFactory.create(INSERT_UPDATE.replace("{PREFIXES}", prefixString)
                                                             .replace("{TRIPLES}", triplesToInsert)
                                                             .replace("{ONTOLOGY_IRI}", "<" + ontologyIRI + ">")
                                               );
        try {
            var qexec = UpdateExecutionFactory.create(insertUpdate, DatasetFactory.create(model));
            qexec.execute();
        } catch (Exception e) {
            throw new UpdateException("Failed to create ontology", e);
        }
    }

    public OntologyDTO getOntology() {
        return OntologyFactory.createOntologyDTO(model);
    }

    public void replaceOntology(OntologyDTO newOntologyDTO) {
        var dctLanguageTag = getDCTLanguageTag(newOntologyDTO);
        var prefixString = buildPrefixString();
        var triplesToInsert = new StringBuilder();
        for (var entry : newOntologyDTO.getEntries()) {
            triplesToInsert.append(ontologyEntryToString(getOntologyIRI(newOntologyDTO), entry, dctLanguageTag));
        }
        var replaceUpdate = UpdateFactory.create(
                  REPLACE_UPDATE.replace("{PREFIXES}", prefixString)
                                .replace("{TRIPLES}", triplesToInsert.toString())
                                .replace("{ONTOLOGY_IRI}", "<" + getOntologyIRI(newOntologyDTO) + ">")
                                                );
        try {
            var qexec = UpdateExecutionFactory.create(replaceUpdate, DatasetFactory.create(model));
            qexec.execute();
        } catch (Exception e) {
            throw new UpdateException("Failed to replace ontology", e);
        }
    }

    public void deleteOntology() {
        var deleteUpdate = UpdateFactory.create(DELETE_UPDATE);

        try {
            var qexec = UpdateExecutionFactory.create(deleteUpdate, DatasetFactory.create(model));
            qexec.execute();
        } catch (Exception e) {
            throw new UpdateException("Failed to delete ontology", e);
        }
    }

    private StringBuilder ontologyEntryToString(String ontologyIRI, OntologyEntry ontologyEntry, String dctLanguageTag) {
        if (!ontologyEntry.isValidEntry()) {
            throw new IllegalArgumentException("Invalid ontology entry");
        }
        // SUBJECT
        var stringBuilder = new StringBuilder().append("<")
                                               .append(ontologyIRI)
                                               .append(">");
        // PREDICATE
        stringBuilder.append(" <")
                     .append(ontologyEntry.getIri())
                     .append(">");
        // OBJECT
        if (ontologyEntry.isIriEntry()) {
            stringBuilder.append(" <")
                         .append(ontologyEntry.getValue())
                         .append(">");
        } else {
            stringBuilder.append(" \"")
                         .append(ontologyEntry.getValue())
                         .append("\"");
            if (dctLanguageTag != null && (ontologyEntry.getDatatypeIri() == null || ontologyEntry.getDatatypeIri().equals(XSDDatatype.XSDstring.getURI()))) {
                stringBuilder.append("@")
                             .append(dctLanguageTag);
            } else if (ontologyEntry.getDatatypeIri() != null) {
                stringBuilder.append("^^")
                             .append("<")
                             .append(ontologyEntry.getDatatypeIri())
                             .append(">");
            }
        }
        return stringBuilder.append(".\n");
    }

    private String buildPrefixString() {
        var prefixString = new StringBuilder();
        for (var prefixPair : model.getNsPrefixMap().entrySet()) {
            prefixString.append("PREFIX ")
                        .append(prefixPair.getKey())
                        .append(": <")
                        .append(prefixPair.getValue())
                        .append(">\n");
        }
        return prefixString.toString();
    }

    /**
     * returns the language tag defined in dct:language for the ontology, or null if the tag is not defined
     *
     * @param ontologyDTO the ontology DTO to extract the language tag from
     *
     * @return the language tag or null
     */
    private String getDCTLanguageTag(OntologyDTO ontologyDTO) {
        var dctLangTag = ontologyDTO.getEntries().stream()
                                    .filter(ontologyEntry -> Objects.equals(ontologyEntry.getIri(), DCTerms.language.getURI()))
                                    .findFirst()
                                    .orElse(null);
        if (dctLangTag != null) {
            return dctLangTag.getValue();
        }
        return null;
    }

    private String getOntologyIRI(OntologyDTO ontologyDTO) {
        return ontologyDTO.getNamespace() + "Ontology";
    }
}
