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

package org.rdfarchitect.shacl.property.shapebuilder;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.Collection;

@Setter
@Accessors(chain = true)
public class NodeShapeBuilder {

    private final Model shaclModel;

    // set by user
    private boolean closed = false;
    private PrefixEntry prefixEntry;
    private Collection<Resource> propertyShapes;
    private String targetClassUri;

    public NodeShapeBuilder(Model resultModel) {
        this.shaclModel = resultModel;
    }

    public Resource build() {
        var nodeShape = shaclModel.createResource(prefixEntry.getUri() + new URI(targetClassUri).getSuffix());
        nodeShape.addProperty(RDF.type, ResourceFactory.createResource(SHACL.NodeShape.getURI()));
        nodeShape.addProperty(ResourceFactory.createProperty(SHACL.targetClass.getURI()), shaclModel.createResource(targetClassUri));

        if (closed) {
            var ignoredPropertiesList = shaclModel.createList(RDF.type);
            nodeShape.addProperty(ResourceFactory.createProperty(SHACL.ignoredProperties.getURI()), ignoredPropertiesList);
            nodeShape.addProperty(ResourceFactory.createProperty(SHACL.closed.getURI()), shaclModel.createTypedLiteral(true));
        }
        var shProperty = ResourceFactory.createProperty(SHACL.property.getURI());
        propertyShapes.forEach(propertyShape -> nodeShape.addProperty(shProperty, propertyShape));
        return nodeShape;
    }
}
