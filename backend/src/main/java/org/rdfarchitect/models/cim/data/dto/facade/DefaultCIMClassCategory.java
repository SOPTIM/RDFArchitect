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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

public class DefaultCIMClassCategory implements ICIMClassCategory {

    private final String graphUri;

    private final Model model;

    public DefaultCIMClassCategory(String graphUri, Model model) {
        this.graphUri = graphUri;
        this.model = model;
    }

    @Override
    public UUID getUuid() {
        return null;
    }

    @Override
    public String getGraphUri() {
        return graphUri;
    }

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public RDFSLabel getLabel() {
        return new RDFSLabel("default");
    }

    @Override
    public RDFSComment getComment() {
        return null;
    }


    @Override
    public List<ICIMClass> getClasses() {
        var classResources = model.listSubjectsWithProperty(RDF.type, RDFS.Class).filterDrop(resource -> resource.hasProperty(RDFS.subClassOf)).toList();
        var classes = new ArrayList<ICIMClass>();
        for (var classResource : classResources){
            classes.add(new CIMClass(graphUri, model, classResource));
        }
        return classes;
    }
}
