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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class CIMResource implements ICIMResource {

    private final String graphUri;

    private final Model model;

    private final UUID uuid;

    protected CIMResource(String graphUri, Model model, UUID uuid) {
        this.graphUri = graphUri;
        this.model = model;
        this.uuid = uuid;
    }

    protected CIMResource(String graphUri, Model model, Resource resource) {
        this.graphUri = graphUri;
        this.model = model;
        var uuidObjects = this.model.listObjectsOfProperty(resource, RDFA.uuid).toList();
        if(uuidObjects.isEmpty()){
            throw new IllegalStateException("No uuid found for resource");
        }
        if(uuidObjects.size() > 1){
            throw new IllegalStateException("Multiple uuids found for single resource.");
        }
        var subject = uuidObjects.getFirst().asLiteral();
        if(!subject.isLiteral()){
            throw new IllegalStateException("UUID for resource is not a literal.");
        }
        this.uuid = UUID.fromString(subject.getLexicalForm());
    }

    protected Model getModel() {
        return this.model;
    }

    @Override
    public UUID getUuid(){
        return uuid;
    }

    @Override
    public String getGraphUri() {
        return this.graphUri;
    }

    @Override
    public URI getUri(){
        return new URI(getJenaResource().getURI());
    }

    @Override
    public RDFSLabel getLabel(){
        var resource = getRequiredUniqueJenaProperty(RDFS.label);
        if(!resource.isLiteral()){
            throw new IllegalStateException("Label for resource with UUID " + uuid + " is not a literal.");
        }
        var langLiteral = resource.asLiteral();
        return new RDFSLabel(langLiteral.getString(), langLiteral.getLanguage());
    }

    @Override
    public RDFSComment getComment(){
        var resource = getUniqueJenaProperty(RDFS.comment);
        if(resource == null){
            return null;
        }
        if(!resource.isLiteral()){
            throw new IllegalStateException("Comment for resource with UUID " + uuid + " is not a literal.");
        }
        var langLiteral = resource.asLiteral();
        return new RDFSComment(langLiteral.getString(), new URI(langLiteral.getDatatype().getURI()));
    }


    // helper

    protected Resource getJenaResource(){
        var subjectsWithUuid = model.listSubjectsWithProperty(RDFA.uuid, uuid.toString()).toList();
        if(subjectsWithUuid.isEmpty()){
            throw new IllegalStateException("Resource with UUID " + uuid + " not found.");
        }
        if(subjectsWithUuid.size() > 1){
            throw new IllegalStateException("Multiple uris found for " +  uuid + ".");
        }
        var subject = subjectsWithUuid.getFirst();
        if(!subject.isURIResource()){
            throw new IllegalStateException("Resource with UUID " + uuid + " is not an URI resource.");
        }
        return subject;
    }

    protected List<Resource> getJenaProperties(Property property){
        var resource = getJenaResource();
        var properties = model.listObjectsOfProperty(resource, property).toList();
        var res = new ArrayList<Resource>();
        for(var prop : properties){
            res.add(prop.asResource());
        }
        return res;
    }

    protected Resource getUniqueJenaProperty(Property property){
        var properties = getJenaProperties(property);
        if(properties.isEmpty()){
            return null;
        }
        if(properties.size() > 1){
            throw new IllegalStateException("Multiple " + property + " properties found for resource with UUID " + uuid + ".");
        }
        return properties.getFirst();
    }

    protected Resource getRequiredUniqueJenaProperty(Property property){
        var propertyResource = getUniqueJenaProperty(property);
        if(propertyResource == null){
            throw new IllegalStateException("Required property " + property + " not found for resource with UUID " + uuid + ".");
        }
        return propertyResource;
    }
}
