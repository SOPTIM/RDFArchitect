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

package org.rdfarchitect.models.cim.data.dto.facade;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CIMClass extends CIMResource implements ICIMClass {

    public CIMClass(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMClass(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    @Override
    public List<ICIMClass> getSuperClasses() {
        var resources = this.getJenaProperties(RDFS.subClassOf);
        var superClasses = new ArrayList<ICIMClass>();
        for(var res : resources){
            superClasses.add(new CIMClass(this.getGraphUri(), this.getModel(), res));
        }
        return superClasses;
    }

    @Override
    public ICIMClassCategory getBelongsToCategory() {
    }

    @Override
    public List<CIMSStereotype> getStereotypes() {
        return List.of();
    }

    @Override
    public List<ICIMAttribute> getAttributes() {
        return List.of();
    }

    @Override
    public List<ICIMAssociation> getAssociations() {
        return List.of();
    }

    @Override
    public List<ICIMEnumEntry> getEnumEntries() {
        return List.of();
    }
}
