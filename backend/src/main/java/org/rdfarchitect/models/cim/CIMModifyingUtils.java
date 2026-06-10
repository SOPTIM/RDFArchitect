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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

@UtilityClass
public class CIMModifyingUtils {

    private static final String PACKAGE_PREFIX = "Package_";

    /**
     * Standardizes all Package prefixes in a cim graph.
     *
     * @param graph The graph holding the cim schema
     * @param usePackagePrefix Whether the {@code Package_} prefix should be used or not
     */
    public void standardizePackagePrefix(Graph graph, boolean usePackagePrefix) {
        var model = ModelFactory.createModelForGraph(graph);
        var packages = model.listResourcesWithProperty(RDF.type, CIMS.classCategory).toList();

        for (var packageResource : packages) {
            var labelStmt = packageResource.getProperty(RDFS.label);
            if (labelStmt == null) {
                continue;
            }

            var currentLabel = labelStmt.getString();
            var currentUri = packageResource.getURI();
            var sepIdx = Math.max(currentUri.lastIndexOf('#'), currentUri.lastIndexOf('/'));
            var uriBase = currentUri.substring(0, sepIdx + 1);
            var uriSuffix = currentUri.substring(sepIdx + 1);

            var newLabel = applyPrefix(currentLabel, usePackagePrefix);
            var newUriSuffix = applyPrefix(uriSuffix, usePackagePrefix);
            var newUri = uriBase + newUriSuffix;

            applyChanges(
                    packageResource, labelStmt, currentUri, newUri, currentLabel, newLabel, model);
        }
    }

    private String applyPrefix(String value, boolean usePackagePrefix) {
        if (usePackagePrefix) {
            return value.startsWith(PACKAGE_PREFIX) ? value : PACKAGE_PREFIX + value;
        }
        return value.startsWith(PACKAGE_PREFIX) ? value.substring(PACKAGE_PREFIX.length()) : value;
    }

    private void applyChanges(
            Resource packageResource,
            Statement labelStmt,
            String currentUri,
            String newUri,
            String currentLabel,
            String newLabel,
            Model model) {
        var uriChanged = !newUri.equals(currentUri);
        var labelChanged = !newLabel.equals(currentLabel);

        if (uriChanged) {
            changeURIAndLabel(packageResource, labelChanged, newUri, newLabel, model);
        } else if (labelChanged) {
            changeLabel(packageResource, labelStmt, newLabel);
        }
    }

    private void changeURIAndLabel(
            Resource packageResource,
            boolean labelChanged,
            String newUri,
            String newLabel,
            Model model) {
        var newResource = model.createResource(newUri);
        packageResource
                .listProperties()
                .toList()
                .forEach(
                        stmt -> {
                            if (stmt.getPredicate().equals(RDFS.label) && labelChanged) {
                                changeLabel(newResource, stmt, newLabel);
                            } else {
                                newResource.addProperty(stmt.getPredicate(), stmt.getObject());
                            }
                        });

        model.listStatements(null, null, packageResource)
                .toList()
                .forEach(stmt -> model.add(stmt.getSubject(), stmt.getPredicate(), newResource));
        model.listStatements(null, null, packageResource).toList().forEach(model::remove);

        model.removeAll(packageResource, null, null);
    }

    private void changeLabel(Resource packageResource, Statement labelStmt, String newLabel) {
        var lang = labelStmt.getLanguage();
        packageResource.removeAll(RDFS.label);
        if (lang != null && !lang.isEmpty()) {
            packageResource.addProperty(RDFS.label, newLabel, lang);
        } else {
            packageResource.addProperty(RDFS.label, newLabel);
        }
    }

    /**
     * Replaces the current datatype of the comments with {@code xsd:string}
     *
     * @param graph the graph holding the cim schema
     */
    public void replaceCommentDatatype(Graph graph) {
        graph.find(Node.ANY, RDFS.comment.asNode(), Node.ANY)
                .toList()
                .forEach(
                        triple -> {
                            var newComment =
                                    new RDFSComment(
                                            triple.getObject().getLiteralLexicalForm(),
                                            new URI("http://www.w3.org/2001/XMLSchema#string"));
                            graph.delete(triple);
                            graph.add(
                                    triple.getSubject(),
                                    RDFS.comment.asNode(),
                                    newComment.asTypedLiteral().asNode());
                        });
    }
}
