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

import lombok.experimental.Delegate;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.NsIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.NTripleWriter;
import org.apache.jena.rdf.model.impl.NodeIteratorImpl;
import org.apache.jena.rdf.model.impl.NsIteratorImpl;
import org.apache.jena.rdf.model.impl.ResIteratorImpl;
import org.apache.jena.rdf.model.impl.StmtIteratorImpl;
import org.apache.jena.rdfxml.xmloutput.impl.RDFXML_Basic;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.rdfarchitect.rdf.graph.wrapper.SortedGraph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Model wrapper that ensures that all iterators return their elements in alphabetical order.
 * Also, when writing the model to an output stream or writer, the statements are written in alphabetical order.
 * Supported formats are RDF/XML, N-Triples, Turtle, TriG and JSON-LD.
 * Other formats can still be written, although there is no guarantee that they will be sorted
 */
public class AlphabeticallySortedModel implements Model {

    @Delegate
    private final Model model;

    public AlphabeticallySortedModel() {
        this(ModelFactory.createDefaultModel());
    }

    public AlphabeticallySortedModel(Model model) {
        this.model = ModelFactory.createModelForGraph(new SortedGraph(Comparator.comparing(Triple::toString), model.getGraph()));
    }

    @Override
    public ResIterator listSubjects() {
        var list = this.model.listSubjects().toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public NsIterator listNameSpaces() {
        var list = this.model.listNameSpaces().toList();
        list.sort(Comparator.comparing(Object::toString));
        return new NsIteratorImpl(list.iterator(), null);
    }

    @Override
    public Model write(Writer writer) {
        return this.write(writer, null, null);
    }

    @Override
    public Model write(Writer writer, String lang) {
        return this.write(writer, lang, null);
    }

    @Override
    public Model write(Writer writer, String lang, String base) {
        try (var out = new ByteArrayOutputStream()) {
            this.write(out, lang, base);
            writer.write(out.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Model write(OutputStream out) {
        return this.write(out, null, null);
    }

    @Override
    public Model write(OutputStream out, String lang) {
        return this.write(out, lang, null);
    }

    @Override
    public Model write(OutputStream out, String lang, String base) {
        var rdfLang = RDFLanguages.nameToLang(lang);
        if (rdfLang == null) {
            throw new IllegalArgumentException("Unsupported language: " + lang);
        }
        if (rdfLang == Lang.RDFXML) {
            var w = new RDFXML_Basic();
            w.write(this, out, base);
        } else {
            RDFDataMgr.write(out, this, rdfLang);
        }
        return this;
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, boolean object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, char object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, long object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, int object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, float object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listLiteralStatements(Resource subject, Property predicate, double object) {
        var list = this.model.listLiteralStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listStatements(Resource subject, Property predicate, String object) {
        var list = this.model.listStatements(subject, predicate, object).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listStatements(Resource subject, Property predicate, String object, String lang) {
        var list = this.model.listStatements(subject, predicate, object, lang).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listStatements(Resource subject, Property predicate, String object, String lang, String direction) {
        var list = this.model.listStatements(subject, predicate, object, lang, direction).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, boolean o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, long o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, char o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, float o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, double o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, Object o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listSubjectsWithProperty(Property p, String str) {
        var list = this.model.listSubjectsWithProperty(p, str).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listSubjectsWithProperty(Property p, String str, String lang) {
        var list = this.model.listSubjectsWithProperty(p, str, lang).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listSubjectsWithProperty(Property p, String str, String lang, String dir) {
        var list = this.model.listSubjectsWithProperty(p, str, lang, dir).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listSubjectsWithProperty(Property p) {
        var list = this.model.listSubjectsWithProperty(p).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p) {
        var list = this.model.listResourcesWithProperty(p).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listSubjectsWithProperty(Property p, RDFNode o) {
        var list = this.model.listSubjectsWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public ResIterator listResourcesWithProperty(Property p, RDFNode o) {
        var list = this.model.listResourcesWithProperty(p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new ResIteratorImpl(list.iterator());
    }

    @Override
    public NodeIterator listObjects() {
        var list = this.model.listObjects().toList();
        list.sort(Comparator.comparing(Object::toString));
        return new NodeIteratorImpl(list.iterator(), null);
    }

    @Override
    public NodeIterator listObjectsOfProperty(Property p) {
        var list = this.model.listObjectsOfProperty(p).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new NodeIteratorImpl(list.iterator(), null);
    }

    @Override
    public NodeIterator listObjectsOfProperty(Resource s, Property p) {
        var list = this.model.listObjectsOfProperty(s, p).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new NodeIteratorImpl(list.iterator(), null);
    }

    @Override
    public StmtIterator listStatements() {
        var list = this.model.listStatements().toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public StmtIterator listStatements(Resource s, Property p, RDFNode o) {
        var list = this.model.listStatements(s, p, o).toList();
        list.sort(Comparator.comparing(Object::toString));
        return new StmtIteratorImpl(list.iterator());
    }

    @Override
    public Map<String, String> getNsPrefixMap() {
        Map<String, String> map = this.model.getNsPrefixMap();
        return map.entrySet().stream()
                  .sorted(Map.Entry.comparingByKey())
                  .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            java.util.LinkedHashMap::new
                                           ));
    }
}
