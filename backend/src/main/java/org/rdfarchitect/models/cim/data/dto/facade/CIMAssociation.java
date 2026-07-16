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
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import java.util.UUID;

public class CIMAssociation extends CIMResource implements ICIMAssociation {

    public CIMAssociation(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMAssociation(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    @Override
    public CIMSMultiplicity getMultiplicity() {
        var multiplicity = getRequiredUniqueJenaProperty(CIMS.multiplicity);
        return new CIMSMultiplicity(multiplicity.getURI());
    }

    @Override
    public ICIMClass getDomain() {
        var domain = getRequiredUniqueJenaProperty(RDFS.domain);
        return CIMClass.fromResource(getGraphUri(), getModel(), domain);
    }

    @Override
    public ICIMClass getRange() {
        var range = getRequiredUniqueJenaProperty(RDFS.range);
        return CIMClass.fromResource(getGraphUri(), getModel(), range);
    }

    @Override
    public ICIMAssociation getInverseAssociation() {
        var inverse = getRequiredUniqueJenaProperty(CIMS.inverseRoleName);
        return new CIMAssociation(getGraphUri(), getModel(), inverse);
    }

    @Override
    public CIMSAssociationUsed getAssociationUsed() {
        var node = getUniqueJenaPropertyNode(CIMS.associationUsed);
        if(node == null){
            throw new IllegalStateException("Required property " + CIMS.associationUsed + " not found for association with UUID " + getUuid() + ".");
        }
        if(!node.isLiteral()){
            throw new IllegalStateException("AssociationUsed for association with UUID " + getUuid() + " is not a literal.");
        }
        return new CIMSAssociationUsed(node.asLiteral().getLexicalForm());
    }
}
