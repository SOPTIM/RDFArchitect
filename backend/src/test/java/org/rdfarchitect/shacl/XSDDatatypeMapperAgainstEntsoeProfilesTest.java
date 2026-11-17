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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

/** Every primitive an official schema declares has to map to a datatype that exists. */
class XSDDatatypeMapperAgainstEntsoeProfilesTest {

    private static final Path LIBRARY = Path.of("../external/entsoe-application-profiles-library");

    @Test
    void noOfficialPrimitiveEndsUpAsAnInventedDatatype() throws IOException {
        var labels = new TreeSet<String>();
        try (var walk = Files.walk(LIBRARY)) {
            walk.filter(path -> path.toString().contains("RDFS"))
                    .filter(path -> path.toString().endsWith(".rdf"))
                    .forEach(path -> labels.addAll(primitiveLabels(path)));
        }

        // Guards against the walk or the stereotype check quietly finding nothing.
        assertThat(labels).contains("Float", "MonthDay", "LangString");
        var invented =
                labels.stream()
                        .filter(
                                label ->
                                        TypeMapper.getInstance()
                                                        .getTypeByName(
                                                                XSDDatatypeMapper
                                                                        .classLabelToDatatype(label)
                                                                        .getURI())
                                                == null)
                        .toList();
        assertThat(invented).isEmpty();
    }

    private static TreeSet<String> primitiveLabels(Path file) {
        Model schema = RDFParser.source(file).lang(Lang.RDFXML).toModel();
        var labels = new TreeSet<String>();
        schema.listStatements(null, CIMS.stereotype, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().toString().endsWith("Primitive"))
                .forEach(
                        statement -> {
                            var label = statement.getSubject().getProperty(RDFS.label);
                            if (label != null) {
                                labels.add(label.getString());
                            }
                        });
        return labels;
    }
}
