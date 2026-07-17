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

import lombok.RequiredArgsConstructor;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class CIMModelFacade implements ICIMModelFacade {

    private final String graphUri;

    private final Model model;

    @Override
    public String getGraphUri() {
        return this.graphUri;
    }

    @Override
    public List<ICIMClass> getCIMClasses() {
        var classResources = model.listSubjectsWithProperty(RDF.type, RDFS.Class).toList();
        var classes = new ArrayList<ICIMClass>();
        for (var classResource : classResources) {
            classes.add(new CIMClass(this.graphUri, this.model, classResource));
        }
        return classes;
    }

    @Override
    public List<ICIMClassCategory> getCIMClassCategories() {
        var packageResources =
                new LinkedHashSet<Resource>(
                        model.listSubjectsWithProperty(RDF.type, CIMS.classCategory).toList());
        model.listObjectsOfProperty(CIMS.belongsToCategory)
                .filterKeep(RDFNode::isURIResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(resource -> resource.hasProperty(RDFA.uuid))
                .forEach(packageResources::add);
        var packages = new ArrayList<ICIMClassCategory>();
        for (var packageResource : packageResources) {
            packages.add(CIMClassCategory.fromResource(this.graphUri, this.model, packageResource));
        }
        packages.add(new DefaultCIMClassCategory(this.graphUri, this.model));
        return packages;
    }

    @Override
    public ICIMClassCategory getCIMClassCategory(UUID uuid) {
        if (uuid == null) {
            return new DefaultCIMClassCategory(this.graphUri, this.model);
        }
        var categoryResources =
                model.listSubjectsWithProperty(RDFA.uuid, uuid.toString())
                        .filterKeep(this::isClassCategory)
                        .toList();
        if (categoryResources.isEmpty()) {
            return null;
        }
        return new CIMClassCategory(this.graphUri, this.model, categoryResources.getFirst());
    }

    private boolean isClassCategory(Resource resource) {
        return resource.hasProperty(RDF.type, CIMS.classCategory)
                || model.contains(null, CIMS.belongsToCategory, resource);
    }

    public List<ICIMAttribute> getCIMAttributes() {
        var attributeResources =
                model.listSubjectsWithProperty(CIMS.stereotype, CIMStereotypes.attribute).toList();
        var attributes = new ArrayList<ICIMAttribute>();
        for (var attributeResource : attributeResources) {
            attributes.add(new CIMAttribute(this.graphUri, this.model, attributeResource));
        }
        return attributes;
    }

    public List<ICIMAssociation> getCIMAssociations() {
        var associationResources = model.listSubjectsWithProperty(CIMS.inverseRoleName).toList();
        var associations = new ArrayList<ICIMAssociation>();
        for (var associationResource : associationResources) {
            associations.add(new CIMAssociation(this.graphUri, this.model, associationResource));
        }
        return associations;
    }
}
