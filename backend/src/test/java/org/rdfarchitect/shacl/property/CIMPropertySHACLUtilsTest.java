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

package org.rdfarchitect.shacl.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** The {@code sh:order} a generated property shape gets. */
class CIMPropertySHACLUtilsTest {

    private static final String LIBRARY = "../external/entsoe-application-profiles-library/";

    /**
     * What the order is: the property's position among the used properties of its class, sorted
     * case-insensitively, counted from 1, and 0.1 for those of {@code IdentifiedObject}. Written as
     * the query it was defined by, so the reading through the graph is held to it.
     */
    private static double orderByQuery(Model ontology, String propertyUri) {
        var query =
                """
                PREFIX cims:    <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#>
                PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>

                SELECT ?property ?class
                WHERE {
                    <PROPERTY_URI> rdfs:domain ?class.
                    ?property   a rdf:Property;
                                rdfs:domain ?class.
                    FILTER NOT EXISTS {
                        ?property cims:AssociationUsed "No".
                    }
                }ORDER BY LCASE(STR(?property))
                """
                        .replace("PROPERTY_URI", propertyUri);
        var properties = new ArrayList<String>();
        try (var execution = QueryExecutionFactory.create(query, ontology)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var result = results.next();
                var property = result.getResource("property").getURI();
                var domain = result.getResource("class").getURI();
                if (property.equals(propertyUri)
                        && new URI(domain).getSuffix().equals("IdentifiedObject")) {
                    return 0.1;
                }
                properties.add(property);
            }
        }
        return properties.indexOf(propertyUri) + 1.0;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CGMES/CurrentRelease/RDFS/61970-600-2_Equipment-AP-Voc-RDFS2020.rdf",
                "CGMES/CurrentRelease/RDFS/61970-600-2_Dynamics-AP-Voc-RDFS2020.rdf",
                "CGMES/CurrentRelease/RDFS/61970-600-2_GeographicalLocation-AP-Voc-RDFS2020.rdf",
                "NCP/CurrentRelease/RDFS/RemedialAction-AP-Voc-RDFS2020.rdf",
                "NCP/CurrentRelease/RDFS/DatasetMetadata-AP-Voc-RDFS2020.rdf"
            })
    void everyPropertyOfAnOfficialProfileGetsTheOrderTheQueryGives(String file) {
        var ontology = ModelFactory.createDefaultModel();
        RDFParser.source(Path.of(LIBRARY + file).toUri().toString()).parse(ontology);

        Map<String, Double> expected = new LinkedHashMap<>();
        Map<String, Double> actual = new LinkedHashMap<>();
        for (Resource property :
                ontology.listSubjectsWithProperty(RDF.type, RDF.Property).toList()) {
            if (property.isURIResource()) {
                expected.put(property.getURI(), orderByQuery(ontology, property.getURI()));
                actual.put(
                        property.getURI(),
                        CIMPropertySHACLUtils.getOrder(ontology, property.getURI()));
            }
        }

        assertThat(expected).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void anUnusedAssociationEndAndAnotherClassesPropertyDoNotCount() {
        var ontology =
                RDFParser.fromString(
                                """
                                @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                                @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
                                @prefix cim:  <http://ex.org/cim#> .

                                cim:Line.b a rdf:Property ; rdfs:domain cim:Line .
                                cim:Line.A a rdf:Property ; rdfs:domain cim:Line .
                                cim:Line.Unused a rdf:Property ; rdfs:domain cim:Line ;
                                    cims:AssociationUsed "No" .
                                cim:Line.c a rdf:Property ; rdfs:domain cim:Line .
                                cim:Bay.a a rdf:Property ; rdfs:domain cim:Bay .
                                cim:IdentifiedObject.name a rdf:Property ;
                                    rdfs:domain cim:IdentifiedObject .
                                """,
                                Lang.TURTLE)
                        .toModel();

        assertThat(CIMPropertySHACLUtils.getOrder(ontology, "http://ex.org/cim#Line.A"))
                .isEqualTo(1.0);
        assertThat(CIMPropertySHACLUtils.getOrder(ontology, "http://ex.org/cim#Line.c"))
                .isEqualTo(3.0);
        assertThat(CIMPropertySHACLUtils.getOrder(ontology, "http://ex.org/cim#Bay.a"))
                .isEqualTo(1.0);
        assertThat(
                        CIMPropertySHACLUtils.getOrder(
                                ontology, "http://ex.org/cim#IdentifiedObject.name"))
                .isEqualTo(0.1);
    }
}
