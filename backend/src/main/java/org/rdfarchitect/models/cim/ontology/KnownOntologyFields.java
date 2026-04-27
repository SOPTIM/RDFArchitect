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

import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.api.dto.ontology.OntologyField;

import java.util.List;

/**
 * Container class for known ontology field definitions. Based on IEC 61970-600-2 CGMES 3.0.1
 * ApplicationProfiles
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KnownOntologyFields {

    public static final OntologyField DCT_CONFORMS_TO =
            OntologyField.builder().iri(DCTerms.conformsTo.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_CREATOR =
            OntologyField.builder().iri(DCTerms.creator.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_DESCRIPTION =
            OntologyField.builder().iri(DCTerms.description.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_IDENTIFIER =
            OntologyField.builder().iri(DCTerms.identifier.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_ISSUED =
            OntologyField.builder()
                    .iri(DCTerms.issued.getURI())
                    .isIriEntry(false)
                    .datatypeIri(XSD.dateTime.getURI())
                    .build();

    public static final OntologyField DCT_LANGUAGE =
            OntologyField.builder().iri(DCTerms.language.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_MODIFIED =
            OntologyField.builder()
                    .iri(DCTerms.modified.getURI())
                    .isIriEntry(false)
                    .datatypeIri(XSD.date.getURI())
                    .build();

    public static final OntologyField DCT_PUBLISHER =
            OntologyField.builder().iri(DCTerms.publisher.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_RIGHTS =
            OntologyField.builder().iri(DCTerms.rights.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_RIGHTS_HOLDER =
            OntologyField.builder().iri(DCTerms.rightsHolder.getURI()).isIriEntry(false).build();

    public static final OntologyField DCT_TITLE =
            OntologyField.builder().iri(DCTerms.title.getURI()).isIriEntry(false).build();

    public static final OntologyField OWL_BACKWARD_COMPATIBLE_WITH =
            OntologyField.builder()
                    .iri(OWL2.backwardCompatibleWith.getURI())
                    .isIriEntry(true)
                    .build();

    public static final OntologyField OWL_INCOMPATIBLE_WITH =
            OntologyField.builder().iri(OWL2.incompatibleWith.getURI()).isIriEntry(true).build();

    public static final OntologyField OWL_PRIOR_VERSION =
            OntologyField.builder().iri(OWL2.priorVersion.getURI()).isIriEntry(true).build();

    public static final OntologyField OWL_VERSION_IRI =
            OntologyField.builder().iri(OWL2.versionIRI.getURI()).isIriEntry(true).build();

    public static final OntologyField OWL_VERSION_INFO =
            OntologyField.builder().iri(OWL2.versionInfo.getURI()).isIriEntry(false).build();

    public static final OntologyField DCAT_KEYWORD =
            OntologyField.builder().iri(DCAT.keyword.getURI()).isIriEntry(false).build();

    public static final OntologyField DCAT_LANDING_PAGE =
            OntologyField.builder().iri(DCAT.landingPage.getURI()).isIriEntry(false).build();

    public static final OntologyField DCAT_THEME =
            OntologyField.builder().iri(DCAT.theme.getURI()).isIriEntry(false).build();

    /**
     * Gets a list of all known ontology fields.
     *
     * @return An unmodifiable list of all known ontology fields.
     */
    public static List<OntologyField> getAllFields() {
        return List.of(
                DCT_CONFORMS_TO,
                DCT_CREATOR,
                DCT_DESCRIPTION,
                DCT_IDENTIFIER,
                DCT_ISSUED,
                DCT_LANGUAGE,
                DCT_MODIFIED,
                DCT_PUBLISHER,
                DCT_RIGHTS,
                DCT_RIGHTS_HOLDER,
                DCT_TITLE,
                OWL_BACKWARD_COMPATIBLE_WITH,
                OWL_INCOMPATIBLE_WITH,
                OWL_PRIOR_VERSION,
                OWL_VERSION_IRI,
                OWL_VERSION_INFO,
                DCAT_KEYWORD,
                DCAT_LANDING_PAGE,
                DCAT_THEME);
    }
}
