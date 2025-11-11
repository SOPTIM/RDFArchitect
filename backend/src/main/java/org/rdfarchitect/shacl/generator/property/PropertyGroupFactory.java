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

package org.rdfarchitect.shacl.generator.property;

import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.PrefixEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.shacl.generator.property.shapebuilder.PropertyGroupBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class PropertyGroupFactory {

    private final Model shaclModel;
    private final PrefixEntry shaclPrefix;

    public Collection<Resource> createReferencedPropertyGroups(){
        var propertyGroupList = listReferencedPropertyGroups();

        for (var i = 0; i < propertyGroupList.size(); i++) {
            var propertyGroup = propertyGroupList.get(i);
            new PropertyGroupBuilder(shaclModel)
                    .setPrefixEntry(shaclPrefix)
                    .setGroupName(new URI(propertyGroup.getURI()).getSuffix())
                    .setOrder(i)
                    .build();

        }
        return propertyGroupList;
    }

    private List<Resource> listReferencedPropertyGroups() {
        return new ArrayList<>(shaclModel.listObjectsOfProperty(shaclModel.createProperty(SHACL.group.getURI()))
                .mapWith(object -> object.asResource().getURI())
                .toSet()
                .stream()
                .map(shaclModel::createResource)
                .toList());
    }
}
