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

package org.rdfarchitect.rdf.model.wrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.iterator.ClosableIterator;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

class CimSortedModelTest {

    private CimSortedModel createTestModel() {
        var m = ModelFactory.createDefaultModel();
        var s1 = m.createResource("http://example.org/A");
        var s2 = m.createResource("http://example.org/B");
        var s3 = m.createResource("http://example.org/C");
        var p = m.createProperty("http://example.org/prop");
        m.add(s2, p, "foo");
        m.add(s3, p, "bar");
        m.add(s1, p, "baz");
        m.setNsPrefix("z", "http://example.org/z");
        m.setNsPrefix("a", "http://example.org/a");
        m.setNsPrefix("m", "http://example.org/m");
        return new CimSortedModel(m);
    }

    private CimSortedModel createTestModelWithOntology() {
        var m = ModelFactory.createDefaultModel();
        var ontology = m.createResource("http://example.org/MyOntology");
        var s1 = m.createResource("http://example.org/A");
        var s2 = m.createResource("http://example.org/B");
        var p = m.createProperty("http://example.org/prop");
        m.add(ontology, RDF.type, OWL2.Ontology);
        m.add(ontology, p, "ontologyValue");
        m.add(s1, p, "foo");
        m.add(s2, p, "bar");
        return new CimSortedModel(m);
    }

    private <T> List<String> toStringList(ClosableIterator<T> it) {
        var result = new ArrayList<String>();
        while (it.hasNext()) {
            result.add(it.next().toString());
        }
        it.close();
        return result;
    }

    @Nested
    class listTests {

        @Test
        void listSubjects_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            // Act
            var subjects = toStringList(model.listSubjects());
            // Assert
            assertThat(subjects).isEqualTo(subjects.stream().sorted().toList());
        }

        @Test
        void listNameSpaces_givenUnsortedNamespaces_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            // Act
            var ns = toStringList(model.listNameSpaces());
            // Assert
            assertThat(ns).isEqualTo(ns.stream().sorted().toList());
        }

        @Test
        void listObjects_givenUnsortedObjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            // Act
            var objs = toStringList(model.listObjects());
            // Assert
            assertThat(objs).isEqualTo(objs.stream().sorted().toList());
        }

        @Test
        void listObjectsOfProperty_givenUnsortedObjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.getProperty("http://example.org/prop");
            // Act
            var objs = toStringList(model.listObjectsOfProperty(p));
            // Assert
            assertThat(objs).isEqualTo(objs.stream().sorted().toList());
        }

        @Test
        void listObjectsOfPropertyWithSubject_givenUnsortedObjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.getResource("http://example.org/B");
            var p = model.getProperty("http://example.org/prop");
            // Act
            var objs = toStringList(model.listObjectsOfProperty(s, p));
            // Assert
            assertThat(objs).isEqualTo(objs.stream().sorted().toList());
        }

        @Test
        void listStatements_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            // Act
            var stmts = toStringList(model.listStatements());
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void
                listStatementsWithSubjectPredicateObject_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.getResource("http://example.org/B");
            var p = model.getProperty("http://example.org/prop");
            var o = model.createLiteral("foo");
            // Act
            var stmts = toStringList(model.listStatements(s, p, o));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsBoolean_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/D");
            var p = model.createProperty("http://example.org/propBool");
            model.add(s, p, model.createTypedLiteral(true));
            model.add(s, p, model.createTypedLiteral(false));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, true));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsChar_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/E");
            var p = model.createProperty("http://example.org/propChar");
            model.add(s, p, model.createTypedLiteral('a'));
            model.add(s, p, model.createTypedLiteral('b'));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, 'a'));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsLong_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/F");
            var p = model.createProperty("http://example.org/propLong");
            model.add(s, p, model.createTypedLiteral(1L));
            model.add(s, p, model.createTypedLiteral(2L));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, 1L));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsInt_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/G");
            var p = model.createProperty("http://example.org/propInt");
            model.add(s, p, model.createTypedLiteral(3));
            model.add(s, p, model.createTypedLiteral(2));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, 2));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsFloat_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/H");
            var p = model.createProperty("http://example.org/propFloat");
            model.add(s, p, model.createTypedLiteral(1.1f));
            model.add(s, p, model.createTypedLiteral(2.2f));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, 1.1f));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listLiteralStatementsDouble_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.createResource("http://example.org/I");
            var p = model.createProperty("http://example.org/propDouble");
            model.add(s, p, model.createTypedLiteral(1.1d));
            model.add(s, p, model.createTypedLiteral(2.2d));
            // Act
            var stmts = toStringList(model.listLiteralStatements(s, p, 1.1d));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listStatementsString_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.getResource("http://example.org/A");
            var p = model.getProperty("http://example.org/prop");
            // Act
            var stmts = toStringList(model.listStatements(s, p, "baz"));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listStatementsStringLang_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.getResource("http://example.org/A");
            var p = model.getProperty("http://example.org/prop");
            model.add(s, p, model.createLiteral("baz", "en"));
            model.add(s, p, model.createLiteral("baz", "de"));
            // Act
            var stmts = toStringList(model.listStatements(s, p, "baz", "en"));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listStatementsStringLangDir_givenUnsortedStatements_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var s = model.getResource("http://example.org/A");
            var p = model.getProperty("http://example.org/prop");
            model.add(s, p, model.createLiteral("baz", "en", "ltr"));
            model.add(s, p, model.createLiteral("baz", "en", "rtl"));
            // Act
            var stmts = toStringList(model.listStatements(s, p, "baz", "en", "ltr"));
            // Assert
            assertThat(stmts).isEqualTo(stmts.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyBoolean_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propBool");
            model.add(
                    model.createResource("http://example.org/J"),
                    p,
                    model.createTypedLiteral(true));
            model.add(
                    model.createResource("http://example.org/K"),
                    p,
                    model.createTypedLiteral(false));
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, true));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyLong_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propLong");
            model.add(
                    model.createResource("http://example.org/L"), p, model.createTypedLiteral(1L));
            model.add(
                    model.createResource("http://example.org/M"), p, model.createTypedLiteral(2L));
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, 1L));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyChar_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propChar");
            model.add(
                    model.createResource("http://example.org/N"), p, model.createTypedLiteral('a'));
            model.add(
                    model.createResource("http://example.org/O"), p, model.createTypedLiteral('b'));
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, 'a'));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyFloat_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propFloat");
            model.add(
                    model.createResource("http://example.org/P"),
                    p,
                    model.createTypedLiteral(1.1f));
            model.add(
                    model.createResource("http://example.org/Q"),
                    p,
                    model.createTypedLiteral(2.2f));
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, 1.1f));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyDouble_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propDouble");
            model.add(
                    model.createResource("http://example.org/R"),
                    p,
                    model.createTypedLiteral(1.1d));
            model.add(
                    model.createResource("http://example.org/S"),
                    p,
                    model.createTypedLiteral(2.2d));
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, 1.1d));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyObject_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/propObj");
            RDFNode o = model.createLiteral("obj");
            model.add(model.createResource("http://example.org/T"), p, o);
            model.add(model.createResource("http://example.org/U"), p, o);
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, o));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listSubjectsWithPropertyString_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            model.add(model.createResource("http://example.org/V"), p, "foo");
            model.add(model.createResource("http://example.org/W"), p, "foo");
            // Act
            var res = toStringList(model.listSubjectsWithProperty(p, "foo"));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void
                listSubjectsWithPropertyStringLang_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            model.add(
                    model.createResource("http://example.org/X"),
                    p,
                    model.createLiteral("foo", "en"));
            model.add(
                    model.createResource("http://example.org/Y"),
                    p,
                    model.createLiteral("foo", "en"));
            // Act
            var res = toStringList(model.listSubjectsWithProperty(p, "foo", "en"));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void
                listSubjectsWithPropertyStringLangDir_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            model.add(
                    model.createResource("http://example.org/Z"),
                    p,
                    model.createLiteral("foo", "en", "ltr"));
            model.add(
                    model.createResource("http://example.org/AA"),
                    p,
                    model.createLiteral("foo", "en", "ltr"));
            // Act
            var res = toStringList(model.listSubjectsWithProperty(p, "foo", "en", "ltr"));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listSubjectsWithProperty_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            model.add(model.createResource("http://example.org/AB"), p, "foo");
            model.add(model.createResource("http://example.org/AC"), p, "bar");
            // Act
            var res = toStringList(model.listSubjectsWithProperty(p));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithProperty_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            model.add(model.createResource("http://example.org/AD"), p, "foo");
            model.add(model.createResource("http://example.org/AE"), p, "bar");
            // Act
            var res = toStringList(model.listResourcesWithProperty(p));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listSubjectsWithPropertyRDFNode_givenUnsortedSubjects_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            RDFNode o = model.createLiteral("foo");
            model.add(model.createResource("http://example.org/AF"), p, o);
            model.add(model.createResource("http://example.org/AG"), p, o);
            // Act
            var res = toStringList(model.listSubjectsWithProperty(p, o));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }

        @Test
        void listResourcesWithPropertyRDFNode_givenUnsortedResources_returnsAlphabeticallySorted() {
            // Arrange
            var model = createTestModel();
            var p = model.createProperty("http://example.org/prop");
            RDFNode o = model.createLiteral("foo");
            model.add(model.createResource("http://example.org/AH"), p, o);
            model.add(model.createResource("http://example.org/AI"), p, o);
            // Act
            var res = toStringList(model.listResourcesWithProperty(p, o));
            // Assert
            assertThat(res).isEqualTo(res.stream().sorted().toList());
        }
    }

    @Nested
    class ontologyFirstTests {

        @Test
        void listStatements_givenOntologyResource_returnsOntologyStatementsFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            // Act
            var stmts = toStringList(model.listStatements());
            // Assert
            assertThat(stmts.getFirst()).contains("MyOntology");
            assertThat(stmts.get(1)).contains("MyOntology");
        }

        @Test
        void listSubjects_givenOntologyResource_returnsOntologyFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            // Act
            var subjects = toStringList(model.listSubjects());
            // Assert
            assertThat(subjects.getFirst()).isEqualTo("http://example.org/MyOntology");
            assertThat(subjects.subList(1, subjects.size()))
                    .isEqualTo(subjects.subList(1, subjects.size()).stream().sorted().toList());
        }

        @Test
        void listObjects_givenOntologyResource_returnsOntologyFirst() {
            // Arrange
            var m = ModelFactory.createDefaultModel();
            var ontology = m.createResource("http://example.org/ZZZOntology");
            var p = m.createProperty("http://example.org/ref");
            m.add(ontology, RDF.type, OWL2.Ontology);
            m.add(m.createResource("http://example.org/A"), p, ontology);
            m.add(
                    m.createResource("http://example.org/B"),
                    p,
                    m.createResource("http://example.org/AAA"));
            var model = new CimSortedModel(m);
            // Act
            var objs = toStringList(model.listObjectsOfProperty(p));
            // Assert
            assertThat(objs.getFirst()).isEqualTo("http://example.org/ZZZOntology");
        }

        @Test
        void listResourcesWithProperty_givenOntologyResource_returnsOntologyFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            var p = model.getProperty("http://example.org/prop");
            // Act
            var res = toStringList(model.listResourcesWithProperty(p));
            // Assert
            assertThat(res.getFirst()).isEqualTo("http://example.org/MyOntology");
            assertThat(res.subList(1, res.size()))
                    .isEqualTo(res.subList(1, res.size()).stream().sorted().toList());
        }

        @Test
        void listSubjectsWithProperty_givenOntologyAndRegularResources_returnsOntologyFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            var p = model.getProperty("http://example.org/prop");
            // Act
            var subjects = toStringList(model.listSubjectsWithProperty(p));
            // Assert
            assertThat(subjects.getFirst()).isEqualTo("http://example.org/MyOntology");
            assertThat(subjects.subList(1, subjects.size()))
                    .isEqualTo(subjects.subList(1, subjects.size()).stream().sorted().toList());
        }

        @Test
        void
                listSubjectsWithPropertyRDFNode_givenOntologyAndRegularResources_returnsOntologyFirst() {
            // Arrange
            var m = ModelFactory.createDefaultModel();
            var ontology = m.createResource("http://example.org/ZZZOntology");
            var s1 = m.createResource("http://example.org/A");
            var p = m.createProperty("http://example.org/prop");
            RDFNode o = m.createLiteral("shared");
            m.add(ontology, RDF.type, OWL2.Ontology);
            m.add(ontology, p, o);
            m.add(s1, p, o);
            var model = new CimSortedModel(m);
            // Act
            var subjects = toStringList(model.listSubjectsWithProperty(p, o));
            // Assert
            assertThat(subjects.getFirst()).isEqualTo("http://example.org/ZZZOntology");
        }

        @Test
        void listStatements_givenOntologyAndRegularResources_returnsRemainingStatementsSorted() {
            // Arrange
            var model = createTestModelWithOntology();
            // Act
            var stmts = toStringList(model.listStatements());
            // Assert
            var nonOntologyStmts = stmts.stream().filter(s -> !s.contains("MyOntology")).toList();
            assertThat(nonOntologyStmts).isEqualTo(nonOntologyStmts.stream().sorted().toList());
        }

        @Test
        void listStatementsWithFilter_givenOntologySubject_returnsOntologyStatementsFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            var p = model.getProperty("http://example.org/prop");
            // Act
            var stmts = toStringList(model.listStatements(null, p, (RDFNode) null));
            // Assert
            assertThat(stmts.getFirst()).contains("MyOntology");
        }

        @Test
        void listObjects_givenOntologyAsObject_returnsOntologyFirst() {
            // Arrange
            var m = ModelFactory.createDefaultModel();
            var ontology = m.createResource("http://example.org/ZZZOntology");
            var p = m.createProperty("http://example.org/ref");
            m.add(ontology, RDF.type, OWL2.Ontology);
            m.add(m.createResource("http://example.org/A"), p, ontology);
            m.add(
                    m.createResource("http://example.org/B"),
                    p,
                    m.createResource("http://example.org/AAA"));
            var model = new CimSortedModel(m);
            // Act
            var objs = toStringList(model.listObjects());
            // Assert
            var resourceObjs =
                    objs.stream()
                            .filter(o -> !o.contains("Ontology") || o.contains("ZZZOntology"))
                            .filter(o -> o.startsWith("http://"))
                            .toList();
            assertThat(resourceObjs.getFirst()).isEqualTo("http://example.org/ZZZOntology");
        }

        @Test
        void write_ntriplesWithOntology_writesOntologyTriplesFirst() {
            // Arrange
            var model = createTestModelWithOntology();
            var out = new ByteArrayOutputStream();
            // Act
            model.write(out, "N-TRIPLES");
            var result = out.toString(StandardCharsets.UTF_8);
            // Assert
            var lines = result.lines().filter(l -> !l.isBlank()).toList();
            var firstOntologyLine =
                    lines.stream().filter(l -> l.contains("MyOntology")).findFirst();
            var firstNonOntologyLine =
                    lines.stream().filter(l -> !l.contains("MyOntology")).findFirst();
            assertThat(firstOntologyLine).isPresent();
            assertThat(firstNonOntologyLine).isPresent();
            assertThat(lines.indexOf(firstOntologyLine.get()))
                    .isLessThan(lines.indexOf(firstNonOntologyLine.get()));
        }

        @Test
        void getNsPrefixMap_returnsSortedByKey() {
            // Arrange
            var model = createTestModel();
            // Act
            var prefixMap = model.getNsPrefixMap();
            // Assert
            var keys = new ArrayList<>(prefixMap.keySet());
            assertThat(keys).isEqualTo(keys.stream().sorted().toList());
        }
    }

    @Nested
    class writeTests {

        private CimSortedModel model;
        private ByteArrayOutputStream outputStream;
        private static final String BASE_URI = "http://example.org/";

        @BeforeEach
        void setUp() {
            // Arrange
            model = createTestModel();
            outputStream = new ByteArrayOutputStream();
        }

        @Test
        void write_withRdfXmlLanguage_shouldSerializeAlphabetically() {
            // Arrange
            var language = "RDF/XML";
            // Act
            var result = (CimSortedModel) model.write(outputStream, language, BASE_URI);
            var serializedOutput = outputStream.toString();
            // Assert
            assertThat(result).isSameAs(model);
            assertThat(serializedOutput).isNotEmpty();

            int indexA = serializedOutput.indexOf("rdf:about=\"A\"");
            int indexB = serializedOutput.indexOf("rdf:about=\"B\"");
            int indexC = serializedOutput.indexOf("rdf:about=\"C\"");

            assertThat(indexA).as("Serialized output should contain resource A").isNotNegative();
            assertThat(indexB).as("Serialized output should contain resource B").isNotNegative();
            assertThat(indexC).as("Serialized output should contain resource C").isNotNegative();
            assertThat(indexA).as("Resource A should appear before resource B").isLessThan(indexB);
            assertThat(indexB).as("Resource B should appear before resource C").isLessThan(indexC);
        }

        @Test
        void write_withRdfXmlAndOntology_shouldSerializeOntologyFirst() {
            // Arrange
            var ontologyModel = createTestModelWithOntology();
            var out = new ByteArrayOutputStream();
            var language = "RDF/XML";
            // Act
            ontologyModel.write(out, language, BASE_URI);
            var serializedOutput = out.toString();
            // Assert
            int indexOntology = serializedOutput.indexOf("MyOntology");
            int indexA = serializedOutput.indexOf("rdf:about=\"A\"");
            assertThat(indexOntology)
                    .as("Ontology resource should appear before resource A")
                    .isLessThan(indexA);
        }

        @Test
        void write_withTurtleLanguage_shouldSerializeAlphabetically() {
            // Arrange
            var language = "TURTLE";
            // Act
            var result = (CimSortedModel) model.write(outputStream, language, BASE_URI);
            var serializedOutput = outputStream.toString();
            // Assert
            assertThat(result).isSameAs(model);
            assertThat(serializedOutput).isNotEmpty().containsIgnoringCase("prefix");

            var subjectLines =
                    Arrays.stream(serializedOutput.split("\n"))
                            .filter(
                                    line ->
                                            line.contains("http://example.org/A")
                                                    || line.contains("http://example.org/B")
                                                    || line.contains("http://example.org/C"))
                            .filter(line -> !line.trim().startsWith("#"))
                            .toList();

            var indexA = findFirstIndexContaining(subjectLines, "http://example.org/A");
            var indexB = findFirstIndexContaining(subjectLines, "http://example.org/B");
            var indexC = findFirstIndexContaining(subjectLines, "http://example.org/C");

            assertThat(indexA)
                    .as("Resource A should appear before B and C")
                    .isLessThan(indexB)
                    .isLessThan(indexC);
        }

        private int findFirstIndexContaining(List<String> lines, String substring) {
            return IntStream.range(0, lines.size())
                    .filter(i -> lines.get(i).contains(substring))
                    .findFirst()
                    .orElse(-1);
        }

        @Test
        void write_withNTriplesLanguage_shouldSerializeAlphabetically() {
            // Arrange
            var language = "N-TRIPLES";
            // Act
            var result = (CimSortedModel) model.write(outputStream, language, BASE_URI);
            var serializedOutput = outputStream.toString();
            // Assert
            assertThat(result).isSameAs(model);
            assertThat(serializedOutput).isNotEmpty();

            var tripleLines =
                    Arrays.stream(serializedOutput.split("\n"))
                            .map(String::trim)
                            .filter(line -> line.endsWith(" ."))
                            .toList();

            assertThat(tripleLines).as("Triple lines should not be empty").isNotEmpty();

            var subjects =
                    tripleLines.stream().map(line -> line.split(" ", 2)[0]).distinct().toList();

            var sortedSubjects = new ArrayList<>(subjects);
            sortedSubjects.sort(Comparator.naturalOrder());

            assertThat(subjects)
                    .as("Subjects should appear in alphabetical order")
                    .containsExactlyElementsOf(sortedSubjects);
        }

        @Test
        void write_withUnsupportedLanguage_shouldThrowIllegalArgumentException() {
            // Arrange
            var unsupportedLanguage = "UNSUPPORTED_FORMAT";
            // Act & Assert
            assertThatThrownBy(() -> model.write(outputStream, unsupportedLanguage, BASE_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported language: " + unsupportedLanguage);
        }

        @Test
        void write_withNullBaseUri_shouldSerializeSuccessfully() {
            // Arrange
            var language = "TURTLE";
            // Act
            var result = (CimSortedModel) model.write(outputStream, language, null);
            var serializedOutput = outputStream.toString();
            // Assert
            assertThat(result).isSameAs(model);
            assertThat(serializedOutput).isNotEmpty();
        }

        @Test
        void write_shouldReturnSameModelInstance() {
            // Arrange
            var language = "TURTLE";
            // Act
            CimSortedModel result = (CimSortedModel) model.write(outputStream, language, BASE_URI);
            // Assert
            assertThat(result)
                    .as("Method should return the same model instance for method chaining")
                    .isSameAs(model);
        }

        @Test
        void write_rdfxmlFormat_writesValidRdfXml() {
            // Arrange
            // Act
            model.write(outputStream, "RDF/XML");
            var result = outputStream.toString(StandardCharsets.UTF_8);
            // Assert
            assertThat(result)
                    .contains("rdf:RDF")
                    .contains("http://example.org/A")
                    .contains("http://example.org/B")
                    .contains("http://example.org/C");
        }

        @Test
        void write_turtleFormat_writesValidTurtle() {
            // Arrange
            // Act
            model.write(outputStream, "TURTLE");
            var result = outputStream.toString(StandardCharsets.UTF_8);
            // Assert
            assertThat(result)
                    .contains("http://example.org/A")
                    .contains("http://example.org/B")
                    .contains("http://example.org/C")
                    .contains("http://example.org/prop");
        }

        @Test
        void write_ntriplesFormat_writesSortedNTriples() {
            // Arrange
            // Act
            model.write(outputStream, "N-TRIPLES");
            var result = outputStream.toString(StandardCharsets.UTF_8);
            // Assert
            var lines = result.lines().filter(l -> l.contains("http://example.org/")).toList();
            var sortedLines = lines.stream().sorted().toList();
            assertThat(lines).isEqualTo(sortedLines);
        }
    }
}
