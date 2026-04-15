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

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.ChangeLogEntryDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class OntologyGeneratableEntriesBuilder {

    private final OntologyFacade ontology;

    private final List<OntologyEntry> ontologyEntries;

    public OntologyGeneratableEntriesBuilder(Model model) {
        this.ontology = new OntologyFacade(model);
        this.ontologyEntries = new ArrayList<>();
    }

    public List<OntologyEntry> build() {
        return ontologyEntries;
    }

    /**
     * Generates the dcterms:modified entry based on the provided changelog list and adds it to the ontology entries.
     * If the changelog is empty, return the existing dcterms:modified entry from the ontology if it exists, otherwise do nothing.
     *
     * @param changelogList List of ChangeLogEntry representing the changelog
     *
     * @return The current instance of OntologyGeneratableEntriesBuilder
     */
    public OntologyGeneratableEntriesBuilder generateDCTModified(List<ChangeLogEntryDTO> changelogList) {
        if (changelogList == null || changelogList.isEmpty()) {
            if (ontology.getOntology() != null) {
                ontology.getOntology()
                        .getEntries()
                        .stream()
                        .filter(entry -> entry.getIri().equals(KnownOntologyFields.DCT_MODIFIED.getIri()))
                        .findFirst()
                        .ifPresent(ontologyEntries::add);
            }
            return this;
        }
        final var latestChangeLogEntry = changelogList.getLast();
        final var timeStampString = latestChangeLogEntry.getTimestamp();
        final var formattedTimestamp = LocalDateTime.parse(timeStampString)
                                                    .toLocalDate()
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        ontologyEntries.add(
                  new OntologyEntry(
                            KnownOntologyFields.DCT_MODIFIED,
                            formattedTimestamp
                  ));
        return this;
    }

    /**
     * Generates the dcterms:issued entry and adds it to the ontology entries.
     *
     * @return The current instance of OntologyGeneratableEntriesBuilder
     */
    public OntologyGeneratableEntriesBuilder generateDCTIssued() {
        if (ontology.getOntology() != null) {
            final var first = ontology.getOntology()
                                      .getEntries()
                                      .stream()
                                      .filter(entry -> entry.getIri().equals(KnownOntologyFields.DCT_ISSUED.getIri()))
                                      .findFirst();
            if (first.isPresent()) {
                ontologyEntries.add(first.get());
                return this;
            }
        }
        ontologyEntries.add(
                  new OntologyEntry(
                            KnownOntologyFields.DCT_ISSUED,
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                  ));
        return this;
    }
}
