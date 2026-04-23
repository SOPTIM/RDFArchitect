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

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.shacl.validation.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class SHACLFromCIMGeneratorTest {

    private static final String TEST_DATA_DIR = "src/test/java/org/rdfarchitect/shacl/testdata/";

    private static final PrefixEntry PREFIX_ENTRY =
            PrefixEntry.create("rdfash", "https://example.com/shacl#");

    @BeforeAll
    static void setUp() {
        System.setProperty("http://jena.apache.org/shacl/validation-strict", "true");
    }

    private Model readFile(String fileName) {
        fileName = TEST_DATA_DIR + fileName;
        try (InputStream in = Files.newInputStream(Path.of(fileName))) {
            var model = ModelFactory.createDefaultModel();
            Lang lang = RDFLanguages.filenameToLang(fileName);
            RDFDataMgr.read(model, in, lang);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileName, e);
        }
    }

    @Nested
    class FullyValidGraphTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_NoInstanceData_passes(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = ModelFactory.createDefaultModel();

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            ShLib.printReport(report);
            assertThat(report.conforms()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_valid1_passes(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("valid1.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            ShLib.printReport(report);
            assertThat(report.conforms()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_valid2_passes(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("valid2.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            ShLib.printReport(report);
            assertThat(report.conforms()).isTrue();
        }
    }

    @Nested
    class CardinalityTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityMissingOptionalAssociation_passes(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityMissingOptionalAssociation.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMultipleAssociation_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMultipleAssociation.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.OtherClass>");
            assertThat(entry.message())
                    .isEqualTo("Cardinality violation (association). Upper bound shall be 1.");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMissingRequiredParentAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMissingRequiredParentAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI())
                    .isEqualTo("http://example.org/instances#childMissingParentAtt");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Parent.parentAtt>");
            assertThat(entry.message()).isEqualTo("Missing required property (attribute).");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMultipleParentAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMultipleParentAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Parent.parentAtt>");
            assertThat(entry.message()).isEqualTo("Missing required property (attribute).");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMultipleChildAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMultipleChildAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childAtt>");
            assertThat(entry.message())
                    .isEqualTo("Cardinality violation (attribute). Upper bound shall be 1.");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMultipleChildEnumAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMultipleChildEnumAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childEnumAtt>");
            assertThat(entry.message()).isEqualTo("Missing required property (attribute).");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_cardinalityViolationMissingRequiredChildEnumAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("cardinalityViolationMissingRequiredChildEnumAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childEnumAtt>");
            assertThat(entry.message()).isEqualTo("Missing required property (attribute).");
        }
    }

    @Nested
    class DataTypeTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_inValidDatatypeChildAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("dataTypeViolationChildAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Violation);
            assertThat(entry1.focusNode().getURI())
                    .isEqualTo("http://example.org/instances#child1");
            assertThat(entry1.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childAtt>");
            assertThat(entry1.message())
                    .isEqualTo("The datatype is not literal or it violates the xsd datatype.");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_dataTypeViolationChildEnumAttIsNotIRI_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("dataTypeViolationChildEnumAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Violation);
            assertThat(entry1.focusNode().getURI())
                    .isEqualTo("http://example.org/instances#child1");
            assertThat(entry1.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childEnumAtt>");
            assertThat(entry1.message())
                    .isEqualTo(
                            "The datatype is not an IRI (Internationalized Resource Identifier) or its enumerated value is not part of the profile.");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_inValidDatatypeParentAtt_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("dataTypeViolationParentAtt.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Violation);
            assertThat(entry1.focusNode().getURI())
                    .isEqualTo("http://example.org/instances#child1");
            assertThat(entry1.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Parent.parentAtt>");
            assertThat(entry1.message())
                    .isEqualTo("The datatype is not literal or it violates the xsd datatype.");
        }
    }

    @Nested
    class ValueTypeTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_associationPointsToWrongClass_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("associationPointsToWrongClass.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Violation);
            assertThat(entry1.focusNode().getURI()).isEqualTo("http://example.org/instances#child");
            assertThat(entry1.resultPath())
                    .hasToString(
                            "<http://iec.ch/TC57/CIM100#Child.OtherClass>/<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>");
            assertThat(entry1.message())
                    .isEqualTo(
                            "One of the following occurs: 1) The value type is not IRI; 2) The value type is not the right class.");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_MissingReferencedRessource_warns(Boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("hasTypeWarningMissingReferencedClass.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Warning);
            assertThat(entry1.focusNode().getURI()).isEqualTo("http://example.org/instances#child");
            assertThat(entry1.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.OtherClass>");
            assertThat(entry1.message())
                    .isEqualTo(
                            "The referenced node is missing an rdf:type, therefore a type validation is not possible.");
        }
    }

    @Nested
    class ClosedShapeTests {

        @Test
        void validate_closedShapeWithUnknownProperties_fails() {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("closedShapeWithUnknownProperties.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, true).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry = report.getEntries().iterator().next();
            assertThat(entry.severity()).isEqualTo(Severity.Violation);
            assertThat(entry.focusNode().getURI()).isEqualTo("http://example.org/instances#child1");
            assertThat(entry.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#unknwonProperty>");
            assertThat(entry.value()).hasToString("\"iShouldntExist\"");
        }

        @Test
        void validate_nonClosedShapeWithUnknownProperties_passes() {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("closedShapeWithUnknownProperties.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, false).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isTrue();
        }

        @Test
        void validate_unknownTriplesWithClosedShapes_fails() {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("validWithUnknownProperty.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, true).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
        }

        @Test
        void validate_unknownTriplesWithUnClosedShapes_passes() {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("validWithUnknownProperty.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, false).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isTrue();
        }
    }

    @Nested
    class MixedValidationTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void validate_validAndInvalidClass_fails(boolean closed) {
            // Arrange
            var ontology = readFile("ontology.ttl");
            var instance = readFile("validAndInvalidClass.ttl");

            // Act
            var shacl = new SHACLFromCIMGenerator(ontology, PREFIX_ENTRY, closed).generate();
            var report = ShaclValidator.get().validate(shacl.getGraph(), instance.getGraph());

            // Assert
            assertThat(report.conforms()).isFalse();
            assertThat(report.getEntries()).hasSize(1);
            var entry1 = report.getEntries().iterator().next();
            assertThat(entry1.severity()).isEqualTo(Severity.Violation);
            assertThat(entry1.focusNode().getURI())
                    .isEqualTo("http://example.org/instances#child2");
            assertThat(entry1.resultPath())
                    .hasToString("<http://iec.ch/TC57/CIM100#Child.childAtt>");
            assertThat(entry1.message())
                    .isEqualTo("The datatype is not literal or it violates the xsd datatype.");
        }
    }
}
