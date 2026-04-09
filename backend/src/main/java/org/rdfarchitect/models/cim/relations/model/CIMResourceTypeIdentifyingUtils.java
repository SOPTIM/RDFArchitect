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

package org.rdfarchitect.models.cim.relations.model;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@UtilityClass
public class CIMResourceTypeIdentifyingUtils {

    public enum CimResourceType {
        PACKAGE,
        CLASS,
        ATTRIBUTE,
        ASSOCIATION,
        ENUM_ENTRY,
        ONTOLOGY,
        UNKNOWN,
    }

    private record TypeRule(Predicate<Resource> matches, CimResourceType type) {

    }

    private static final List<TypeRule> TYPE_RULES = List.of(
              new TypeRule(s -> s.hasProperty(RDF.type, CIMS.classCategory), CimResourceType.PACKAGE),
              new TypeRule(s -> s.hasProperty(RDF.type, RDFS.Class), CimResourceType.CLASS),
              new TypeRule(s -> s.hasProperty(RDF.type, OWL2.Ontology), CimResourceType.ONTOLOGY),
              new TypeRule(CIMPropertyUtils::isAttribute, CimResourceType.ATTRIBUTE),
              new TypeRule(CIMPropertyUtils::isAssociation, CimResourceType.ASSOCIATION),
              new TypeRule(CIMResourceTypeIdentifyingUtils::isEnumEntry, CimResourceType.ENUM_ENTRY)
                                                            );

    public CimResourceType getType(Model model, UUID uuid) {
        var subject = findUniqueSubject(model, uuid);

        return TYPE_RULES.stream()
                         .filter(rule -> rule.matches().test(subject))
                         .map(TypeRule::type)
                         .findFirst()
                         .orElse(CimResourceType.UNKNOWN);
    }

    public Resource findUniqueSubject(Model model, UUID uuid) {
        var subjects = model.listSubjectsWithProperty(RDFA.uuid, uuid.toString()).toList();
        if (subjects.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one subject with UUID " + uuid + ", but found " + subjects.size());
        }
        return subjects.getFirst();
    }

    private boolean isEnumEntry(Resource subject) {
        var types = subject.listProperties(RDF.type).toList();
        if (types.size() != 1 || !types.getFirst().getObject().isURIResource()) {
            return false;
        }
        return types.getFirst().getObject().asResource()
                    .hasProperty(CIMS.stereotype, CIMStereotypes.enumeration);
    }
}
