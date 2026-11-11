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

package org.rdfarchitect.xmi.map;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.api.dto.ontology.OntologyField;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.xmi.read.XmiClass;
import org.rdfarchitect.xmi.read.XmiIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ontology header a profile's metadata class states.
 *
 * <p>A CGMES 3.0 profile describes itself through a class named {@code <Profile>_Ontology} whose
 * twenty attributes carry the header as their default values. The release writes that as an {@code
 * owl:Ontology} and not as a class, so writing the class would put twenty properties into the
 * schema that the release has no concept of.
 *
 * <p>Which attribute means what is read off its name: it is the local name of the predicate the
 * release uses, so {@code title} is {@code dcterms:title} and {@code versionIRI} is {@code
 * owl:versionIRI}. The known fields decide the rest — whether the value is an IRI or a literal and
 * which datatype it carries — rather than this guessing per name.
 *
 * <p>The CGMES 2.4.15 convention is different and is left alone: its {@code <Profile>Version} class
 * is a class in the release too, with its attributes as properties, which is what the graph
 * comparison measures.
 *
 * <p>Which class this is, is {@link XmiIndex#headerClassIdsOf} to say, because the closure has to
 * leave out the same one: a header's attribute types and superclass describe the header.
 */
@UtilityClass
public class XmiOntology {

    /** What the export separates the several values of one field with. */
    private final String VALUE_SEPARATOR = "\\|";

    /**
     * A header built from a metadata class.
     *
     * @param unmappedFields the attributes whose name matches no known field, so that an import can
     *     say what it did not carry
     */
    public record BuiltOntology(OntologyDTO ontology, List<String> unmappedFields) {}

    /**
     * The header the class states, or empty when it states nothing that can be written.
     *
     * @param profileNamespace what the ontology is named in; the header has no place without one
     */
    public Optional<BuiltOntology> of(XmiClass cimClass, String profileNamespace) {
        if (profileNamespace == null) {
            return Optional.empty();
        }
        var fieldsByName = knownFieldsByName();
        var entries = new ArrayList<OntologyEntry>();
        var unmapped = new ArrayList<String>();
        for (var attribute : cimClass.getAttributes()) {
            var value = attribute.getDefaultValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            var field = fieldsByName.get(attribute.getName());
            if (field == null) {
                unmapped.add(attribute.getName());
                continue;
            }
            for (var single : value.split(VALUE_SEPARATOR)) {
                if (!single.isBlank()) {
                    entries.add(new OntologyEntry(field, single.strip()));
                }
            }
        }
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        var ontology =
                new OntologyDTO()
                        .setNamespace(profileNamespace)
                        .setUuid(UUID.randomUUID().toString())
                        .setEntries(entries);
        return Optional.of(new BuiltOntology(ontology, List.copyOf(unmapped)));
    }

    /**
     * The known fields by the local name of their predicate, which is what an attribute is named.
     */
    private Map<String, OntologyField> knownFieldsByName() {
        var byName = new LinkedHashMap<String, OntologyField>();
        for (var field : KnownOntologyFields.getAllFields()) {
            byName.put(localNameOf(field.getIri()), field);
        }
        return byName;
    }

    private String localNameOf(String iri) {
        var lastHash = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
        return lastHash < 0 ? iri : iri.substring(lastHash + 1);
    }
}
