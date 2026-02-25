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

package org.rdfarchitect.cim;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import java.util.Objects;

@UtilityClass
public class ValueNodeParser {

    public record ParsedValue(String value, URI dataType, boolean blankNode) {
    }

    public ParsedValue parse(RDFNode node) {
        Objects.requireNonNull(node, "value node must not be null");
        if (node.isLiteral()) {
            return parseLiteral(node);
        }
        if (node.isURIResource()) {
            throw new IllegalArgumentException("Invalid value node shape: URI resources are not allowed for fixed/default values");
        }
        if (node.isAnon()) {
            return parseBlankNode(node.asResource());
        }
        throw new IllegalArgumentException("Invalid value node shape: expected literal or blank node");
    }

    private ParsedValue parseBlankNode(Resource resource) {
        Statement onlyStatement = null;
        var it = resource.listProperties();
        try {
            while (it.hasNext()) {
                if (onlyStatement != null) {
                    throw new IllegalArgumentException("Invalid value node shape: blank node must contain exactly one statement");
                }
                onlyStatement = it.next();
            }
        } finally {
            it.close();
        }
        if (onlyStatement == null) {
            throw new IllegalArgumentException("Invalid value node shape: blank node must contain exactly one rdfs:Literal statement");
        }
        if (!RDFS.Literal.getURI().equals(onlyStatement.getPredicate().getURI())) {
            throw new IllegalArgumentException("Invalid value node shape: blank node predicate must be rdfs:Literal");
        }
        if (!onlyStatement.getObject().isLiteral()) {
            throw new IllegalArgumentException("Invalid value node shape: rdfs:Literal object must be a literal");
        }
        var parsedLiteral = parseLiteral(onlyStatement.getObject());
        return new ParsedValue(parsedLiteral.value(), parsedLiteral.dataType(), true);
    }

    private ParsedValue parseLiteral(RDFNode node) {
        var literal = node.asLiteral();
        var dataTypeUri = literal.getDatatypeURI();
        var datatype = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
        return new ParsedValue(literal.getLexicalForm(), datatype, false);
    }
}
