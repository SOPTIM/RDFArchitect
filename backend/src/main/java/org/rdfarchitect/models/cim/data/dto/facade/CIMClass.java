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
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CIMClass extends CIMResource implements ICIMClass {

    public CIMClass(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMClass(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    public static ICIMClass fromResource(String graphUri, Model model, Resource resource) {
        var inModel = resource.inModel(model);
        if (!inModel.hasProperty(RDFA.uuid) || CIMResourceUtils.isExternalResource(inModel)) {
            return new ExternalCIMClass(graphUri, model, resource);
        }
        return new CIMClass(graphUri, model, resource);
    }

    @Override
    public boolean isExternal() {
        return false;
    }

    @Override
    public RDFSLabel getLabel() {
        if (getUniqueJenaPropertyNode(RDFS.label) == null) {
            return new RDFSLabel(getUri().getSuffix());
        }
        return super.getLabel();
    }

    @Override
    public List<ICIMClass> getSuperClasses() {
        var resources = this.getJenaProperties(RDFS.subClassOf);
        var superClasses = new ArrayList<ICIMClass>();
        for (var res : resources) {
            superClasses.add(fromResource(this.getGraphUri(), this.getModel(), res));
        }
        return superClasses;
    }

    @Override
    public List<ICIMClass> getSubClasses() {
        return subClassesOf(this.getGraphUri(), this.getModel(), this.getJenaResource());
    }

    static List<ICIMClass> subClassesOf(String graphUri, Model model, Resource resource) {
        var subClasses = new ArrayList<ICIMClass>();
        for (var res : byUri(model.listSubjectsWithProperty(RDFS.subClassOf, resource))) {
            subClasses.add(fromResource(graphUri, model, res));
        }
        return subClasses;
    }

    @Override
    public ICIMClassCategory getBelongsToCategory() {
        var category = getUniqueJenaProperty(CIMS.belongsToCategory);
        if (category == null) {
            return null;
        }
        return CIMClassCategory.fromResource(getGraphUri(), getModel(), category);
    }

    @Override
    public List<CIMSStereotype> getStereotypes() {
        return getStereotypeList();
    }

    @Override
    public List<ICIMAttribute> getAttributes() {
        var attributes = new ArrayList<ICIMAttribute>();
        for (var property : listDirectProperties()) {
            if (CIMPropertyUtils.isAttribute(property)) {
                attributes.add(new CIMAttribute(getGraphUri(), getModel(), property));
            }
        }
        return attributes;
    }

    @Override
    public List<ICIMAssociation> getAssociations() {
        var associations = new ArrayList<ICIMAssociation>();
        for (var property : listDirectProperties()) {
            if (CIMPropertyUtils.isAssociation(property)) {
                associations.add(new CIMAssociation(getGraphUri(), getModel(), property));
            }
        }
        return associations;
    }

    @Override
    public List<ICIMEnumEntry> getEnumEntries() {
        var jenaResource = getJenaResource();
        if (!jenaResource.hasProperty(CIMS.stereotype, CIMStereotypes.enumeration)) {
            return List.of();
        }
        var entries = new ArrayList<ICIMEnumEntry>();
        for (var entry : byUri(getModel().listSubjectsWithProperty(RDF.type, jenaResource))) {
            entries.add(new CIMEnumEntry(getGraphUri(), getModel(), entry));
        }
        return entries;
    }

    private List<Resource> listDirectProperties() {
        return byUri(getModel().listSubjectsWithProperty(RDFS.domain, getJenaResource())).stream()
                .filter(property -> getModel().contains(property, RDFA.uuid))
                .toList();
    }

    private static List<Resource> byUri(ResIterator resources) {
        return resources.toList().stream()
                .sorted(Comparator.comparing(resource -> String.valueOf(resource.getURI())))
                .toList();
    }
}
