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

package org.rdfarchitect.services.update.classes;

import lombok.experimental.UtilityClass;

import org.apache.jena.graph.Graph;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;

@UtilityClass
public class GraphClasses {

    public boolean containsClass(Graph graph, URI uri) {
        return graph.contains(uri.toNode(), RDF.type.asNode(), RDFS.Class.asNode());
    }

    public String findClassUUID(Graph graph, URI uri) {
        if (!containsClass(graph, uri)) {
            return null;
        }
        var resource = CIMResourceUtils.findResourceForUri(graph, uri.toString());
        if (!resource.hasProperty(RDFA.uuid)) {
            return null;
        }
        return CIMResourceUtils.findUuidForResource(resource).toString();
    }
}
