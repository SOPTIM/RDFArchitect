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
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

@UtilityClass
public class ValueNodeParser {

    public record ParsedValue(String value, URI dataType, boolean blankNode, URI blankNodePredicate, boolean uriValue) {
    }

    public ParsedValue parse(RDFNode node) {
        if (node == null) {
            return new ParsedValue(null, null, false, null, false);
        }
        if (node.isLiteral()) {
            var literal = node.asLiteral();
            var dataTypeUri = literal.getDatatypeURI();
            var datatype = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
            return new ParsedValue(literal.getLexicalForm(), datatype, false, null, false);
        }
        if (node.isURIResource()) {
            return new ParsedValue(node.asResource().getURI(), null, false, null, true);
        }
        if (node.isAnon()) {
            return parseBlankNode(node.asResource());
        }
        return new ParsedValue(null, null, false, null, false);
    }

    private ParsedValue parseBlankNode(Resource resource) {
        if (resource == null) {
            return new ParsedValue(null, null, true, null, false);
        }
        Statement fallbackStatement = null;
        var it = resource.listProperties();
        try {
            while (it.hasNext()) {
                var stmt = it.next();
                var object = stmt.getObject();
                if (object.isLiteral()) {
                    var literal = object.asLiteral();
                    var dataTypeUri = literal.getDatatypeURI();
                    var datatype = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
                    return new ParsedValue(literal.getLexicalForm(), datatype, true, new URI(stmt.getPredicate().getURI()), false);
                }
                if (fallbackStatement == null) {
                    fallbackStatement = stmt;
                }
                if (object.isURIResource()) {
                    return new ParsedValue(object.asResource().getURI(), null, true, new URI(stmt.getPredicate().getURI()), true);
                }
            }
        } finally {
            it.close();
        }
        if (fallbackStatement != null) {
            var object = fallbackStatement.getObject();
            return new ParsedValue(object.toString(), null, true, new URI(fallbackStatement.getPredicate().getURI()), false);
        }
        return new ParsedValue(null, null, true, null, false);
    }
}
