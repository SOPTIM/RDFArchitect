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
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

@Setter
@Accessors(chain = true)
public class PropertyGroupBuilder {

    private final Model shaclModel;

    private PrefixEntry prefixEntry;

    private String groupName;

    private double order;

    public PropertyGroupBuilder(Model shaclModel) {
        this.shaclModel = shaclModel;
    }

    public Resource build() {
        var propertyGroup = shaclModel.createResource(prefixEntry.getUri() + groupName);
        propertyGroup.addProperty(RDF.type, ResourceFactory.createResource(SHACL.PropertyGroup.getURI()));
        propertyGroup.addLiteral(RDFS.label, groupName);
        propertyGroup.addLiteral(ResourceFactory.createProperty(SHACL.order.getURI()), shaclModel.createTypedLiteral(order, XSDDatatype.XSDdecimal));
        return propertyGroup;
    }

}
