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

package org.rdfarchitect.models.cim;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.Objects;

/**
 * Parses an {@link RDFNode} representing an attribute fixed/default value into a {@link
 * ParsedValue}.
 *
 * <p>Two value shapes are supported:
 *
 * <ul>
 *   <li><b>Direct literal</b>: the lexical form is taken from the literal, the optional XSD
 *       datatype is preserved, {@code blankNode = false}.
 *   <li><b>Blank-node wrapper</b>: an anonymous resource that contains exactly one statement with
 *       predicate {@code rdfs:Literal} and a literal object. The lexical form and datatype are
 *       taken from that literal, {@code blankNode = true}.
 * </ul>
 *
 * <p>Any other shape (URI resources, malformed blank nodes) yields an {@link
 * IllegalArgumentException}.
 */
@UtilityClass
public class ValueNodeParser {

    public record ParsedValue(String value, URI dataType, boolean blankNode) {}

    public ParsedValue parse(RDFNode node) {
        return parse(node, null);
    }

    /**
     * Parses {@code node} into a {@link ParsedValue}.
     *
     * @param node the RDF node to parse, must not be {@code null}.
     * @param lookupModel optional model used to re-attach the node to its owning model so that
     *     blank-node properties can be enumerated (required when {@code node} originates from a
     *     {@link org.apache.jena.query.QuerySolution} that is not bound to the source model).
     * @return the parsed value.
     */
    public ParsedValue parse(RDFNode node, Model lookupModel) {
        Objects.requireNonNull(node, "value node must not be null");
        if (node.isLiteral()) {
            return parseLiteral(node.asLiteral());
        }
        if (node.isURIResource()) {
            throw new IllegalArgumentException(
                    "Invalid value node shape: URI resources are not allowed for fixed/default"
                            + " values");
        }
        if (node.isAnon()) {
            return parseBlankNode(resolveResource(node, lookupModel));
        }
        throw new IllegalArgumentException(
                "Invalid value node shape: expected literal or blank node");
    }

    private Resource resolveResource(RDFNode node, Model lookupModel) {
        if (lookupModel == null) {
            return node.asResource();
        }
        return lookupModel.asRDFNode(node.asNode()).asResource();
    }

    private ParsedValue parseBlankNode(Resource resource) {
        Statement onlyStatement = null;
        var it = resource.listProperties();
        try {
            while (it.hasNext()) {
                if (onlyStatement != null) {
                    throw new IllegalArgumentException(
                            "Invalid value node shape: blank node must contain exactly one"
                                    + " statement");
                }
                onlyStatement = it.next();
            }
        } finally {
            it.close();
        }
        if (onlyStatement == null) {
            throw new IllegalArgumentException(
                    "Invalid value node shape: blank node must contain exactly one rdfs:Literal"
                            + " statement");
        }
        if (!RDFS.Literal.getURI().equals(onlyStatement.getPredicate().getURI())) {
            throw new IllegalArgumentException(
                    "Invalid value node shape: blank node predicate must be rdfs:Literal");
        }
        if (!onlyStatement.getObject().isLiteral()) {
            throw new IllegalArgumentException(
                    "Invalid value node shape: rdfs:Literal object must be a literal");
        }
        var parsedLiteral = parseLiteral(onlyStatement.getObject().asLiteral());
        return new ParsedValue(parsedLiteral.value(), parsedLiteral.dataType(), true);
    }

    private ParsedValue parseLiteral(Literal literal) {
        var dataTypeUri = literal.getDatatypeURI();
        var datatype = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
        return new ParsedValue(literal.getLexicalForm(), datatype, false);
    }
}
