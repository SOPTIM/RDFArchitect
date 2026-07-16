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

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CIMClassCategory extends CIMResource implements ICIMClassCategory {

    public CIMClassCategory(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMClassCategory(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    public static ICIMClassCategory fromResource(String graphUri, Model model, Resource resource) {
        if(resource == null){
            return new DefaultCIMClassCategory(graphUri, model);
        }
        return new CIMClassCategory(graphUri, model, resource);
    }

    @Override
    public List<ICIMClass> getClasses() {
        var resourcesInPackage = getModel().listSubjectsWithProperty(CIMS.belongsToCategory, this.getJenaResource()).toList();
        var classes = new ArrayList<ICIMClass>();
        for(var resource : resourcesInPackage){
            classes.add(CIMClass.fromResource(getGraphUri(), getModel(), resource));
        }
        return classes;
    }
}
