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

package org.rdfarchitect.shacl.property.shapegenerator;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.PrefixEntry;
import org.rdfarchitect.models.cim.relations.model.properties.CIMAssociationUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.shacl.property.CIMPropertySHACLUtils;
import org.rdfarchitect.shacl.property.shapebuilder.ValueTypePropertyShapeBuilder;

public class ValueTypePropertyShapeFromCIMAssociationGenerator implements PropertyShapeFromCIMPropertyGenerator {

    private static final String PROPERTY_GROUP_LABEL = "ValueTypeGroup";

    private Model ontologyModel;

    private Model shaclModel;

    private PrefixEntry shaclPrefix;

    @Override
    public PropertyShapeFromCIMPropertyGenerator setOntologyModel(Model ontology) {
        this.ontologyModel = ontology;
        return this;
    }

    @Override
    public PropertyShapeFromCIMPropertyGenerator setShaclModel(Model shacl) {
        this.shaclModel = shacl;
        return this;
    }

    @Override
    public PropertyShapeFromCIMPropertyGenerator setShaclPrefix(PrefixEntry shaclPrefix) {
        this.shaclPrefix = shaclPrefix;
        return this;
    }

    @Override
    public Resource createPropertyShape(Resource association) {
        if (ontologyModel == null || shaclModel == null || shaclPrefix == null) {
            throw new IllegalStateException("Models and prefix must be set before creating property shapes.");
        }
        if (!CIMPropertyUtils.isAssociation(association) || !CIMAssociationUtils.isUsedAssociation(association)) {
            return null; // This converter only creates shapes for used associations
        }
        var order = CIMPropertySHACLUtils.getOrder(ontologyModel, association.getURI());
        return new ValueTypePropertyShapeBuilder(shaclModel)
                .setPrefixEntry(shaclPrefix)
                .setPropertyUri(association.getURI())
                .setOrder(order)
                .setPropertyGroupUri(shaclPrefix.getUri() + PROPERTY_GROUP_LABEL)
                .setValueTypeUris(CIMAssociationUtils.listAssociationDatatypes(association)
                        .stream()
                        .map(Resource::getURI)
                        .toList()
                )
                .build();
    }
}
