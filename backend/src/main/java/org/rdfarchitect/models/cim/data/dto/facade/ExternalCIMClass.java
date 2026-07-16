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
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import java.util.List;
import java.util.UUID;

public class ExternalCIMClass implements ICIMClass {

    private final String graphUri;

    private final Model model;

    private final Resource resource;

    public ExternalCIMClass(String graphUri, Model model, Resource resource) {
        if(!resource.isURIResource()){
            throw new IllegalStateException("External class resource is not an URI resource.");
        }
        this.graphUri = graphUri;
        this.model = model;
        this.resource = resource;
    }

    @Override
    public UUID getUuid() {
        if(!this.resource.hasProperty(RDFA.uuid)){
            return null;
        }
        return UUID.fromString(this.resource.getProperty(RDFA.uuid).getObject().toString());
    }

    @Override
    public String getGraphUri() {
        return graphUri;
    }

    @Override
    public URI getUri() {
        return new URI(resource.getURI());
    }

    @Override
    public RDFSLabel getLabel() {
        var statement = model.getProperty(resource, RDFS.label);
        if(statement == null || !statement.getObject().isLiteral()){
            return new RDFSLabel(getUri().getSuffix());
        }
        var langLiteral = statement.getObject().asLiteral();
        return new RDFSLabel(langLiteral.getString(), langLiteral.getLanguage());
    }

    @Override
    public RDFSComment getComment() {
        var statement = model.getProperty(resource, RDFS.comment);
        if(statement == null || !statement.getObject().isLiteral()){
            return null;
        }
        var langLiteral = statement.getObject().asLiteral();
        return new RDFSComment(langLiteral.getString(), new URI(langLiteral.getDatatype().getURI()));
    }

    @Override
    public List<ICIMClass> getSuperClasses() {
        return List.of();
    }

    @Override
    public ICIMClassCategory getBelongsToCategory() {
        return null;
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
