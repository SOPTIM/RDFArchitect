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
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;

import java.util.UUID;

public class CIMEnumEntry extends CIMResource implements ICIMEnumEntry {

    public CIMEnumEntry(String graphUri, Model model, UUID uuid) {
        super(graphUri, model, uuid);
    }

    public CIMEnumEntry(String graphUri, Model model, Resource resource) {
        super(graphUri, model, resource);
    }

    @Override
    public ICIMClass getDomain() {
        var domain = getRequiredUniqueJenaProperty(RDF.type);
        return CIMClass.fromResource(getGraphUri(), getModel(), domain);
    }

    @Override
    public CIMSStereotype getStereotype() {
        var stereotypes = getStereotypeList();
        if (stereotypes.isEmpty()) {
            return null;
        }
        return stereotypes.getFirst();
    }
}
