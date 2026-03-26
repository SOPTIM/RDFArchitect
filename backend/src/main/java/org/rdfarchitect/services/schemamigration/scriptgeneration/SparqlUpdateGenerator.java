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

package org.rdfarchitect.services.schemamigration.scriptgeneration;

import lombok.RequiredArgsConstructor;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.update.UpdateRequest;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.cim.queries.templates.SparqlTemplateLoader;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SparqlUpdateGenerator {

    private final MigrationSessionStore migrationSessionStore;

    public String generateDeleteClassUpdate(SemanticClassChange classChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/class-deleted");
        pss.setIri("deletedClass", classChange.getIri());
        return pss.toString();
    }

    public String generateRenameClassUpdate(SemanticClassChange classChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/class-renamed");
        pss.setIri("oldClass", classChange.getOldIRI());
        pss.setIri("newClass", classChange.getIri());
        return pss.toString();
    }

    public String generateAddAttributeUpdate(SemanticAttributeChange attributeChange, String classIri) {
        if (attributeChange.isOptional() && !attributeChange.isForceDefaultValue()) {
            return "";
        }

        var subClasses = getClassHierarchy(classIri);
        var result = new UpdateRequest();

        for (var subClass : subClasses) {
            result.add(generateAddAttributeToSingleClassUpdate(attributeChange, subClass));
        }

        return result.toString();
    }

    public String generateAddAttributeToSingleClassUpdate(SemanticAttributeChange attributeChange, String classIri) {
        if (attributeChange.isOptional() && !attributeChange.isForceDefaultValue()) {
            return "";
        }

        var xsd = TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-added");
        pss.setIri("domain", classIri);
        pss.setIri("attribute", attributeChange.getIri());
        pss.setLiteral("defaultValue", attributeChange.getDefaultValue(), xsd);
        return pss.toString();
    }

    public String generateDeletePropertyUpdate(SemanticResourceChange propertyChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/property-deleted");
        pss.setIri("property", propertyChange.getIri());
        return pss.toString();
    }

    public String generateDeletePropertyFromSingleClassUpdate(SemanticResourceChange propertyChange, String classIri) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/property-deleted");
        pss.setIri("property", propertyChange.getIri());
        pss.setIri("domain", classIri);
        return pss.toString();
    }

    public String generateDomainRenameUpdate(SemanticResourceChange resourceChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/domain-renamed");
        pss.setIri("newIRI", resourceChange.getIri());
        pss.setIri("oldIRI", resourceChange.getOldIRI());
        return pss.toString();
    }

    public String generateRenameAttributeUpdate(SemanticAttributeChange attributeChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-renamed");
        pss.setIri("oldAttribute", attributeChange.getOldIRI());
        pss.setIri("newAttribute", attributeChange.getIri());
        return pss.toString();
    }

    public String generateDatatypeChangedUpdate(SemanticAttributeChange attributeChange) {
        var xsd = TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-datatype-changed");
        pss.setIri("attribute", attributeChange.getIri());
        pss.setLiteral("defaultValue", attributeChange.getDefaultValue(), xsd);
        pss.setIri("newDatatype", attributeChange.getPrimitiveDataType());
        return pss.toString();
    }

    public String generateFixedValueUpdate(SemanticAttributeChange attributeChange) {
        var xsd = TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-fixed-value-changed");
        pss.setIri("attribute", attributeChange.getIri());
        pss.setLiteral("newValue", attributeChange.getDefaultValue(), xsd);
        return pss.toString();
    }

    public String generateRenameEnumEntryUpdate(SemanticEnumEntryChange enumEntryChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/enum-entry-renamed");
        pss.setIri("oldEnumEntry", enumEntryChange.getOldIRI());
        pss.setIri("newEnumEntry", enumEntryChange.getIri());
        return pss.toString();
    }

    public String generateDeleteEnumEntryUpdate(SemanticEnumEntryChange enumEntryChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/enum-entry-deleted");
        pss.setIri("enumEntry", enumEntryChange.getIri());
        pss.setIri("replacementValue", enumEntryChange.getReplacementValue());
        return pss.toString();
    }

    public String generateAddAssociationUpdate(SemanticAssociationChange associationChange) {
        if (!associationChange.isAssociationUsed() ||
                associationChange.getMapping() == null ||
                associationChange.getMapping().isBlank()) {
            return "";
        }

        var subClasses = getClassHierarchy(associationChange.getMapping());
        var result = new UpdateRequest();

        for (var subClass : subClasses) {
            result.add(generateAddAssociationToSingleClassUpdate(associationChange, subClass));
        }

        var updateString = result.toString();
        return updateString.replace("<mapping>", associationChange.getMapping());
    }

    public String generateAddAssociationToSingleClassUpdate(SemanticAssociationChange associationChange, String classIri) {
        if (!associationChange.isAssociationUsed() ||
                associationChange.getMapping() == null ||
                associationChange.getMapping().isBlank()) {
            return "";
        }

        var pss = SparqlTemplateLoader.loadTemplate("migration/association-added");
        pss.setIri("domain", classIri);
        pss.setIri("association", associationChange.getIri());
        var updateString = pss.toString();
        return updateString.replace("<mapping>", associationChange.getMapping());
    }

    public String generateRenameAssociationUpdate(SemanticAssociationChange associationChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/association-renamed");
        pss.setIri("oldAssociation", associationChange.getOldIRI());
        pss.setIri("newAssociation", associationChange.getIri());
        return pss.toString();
    }

    public String generateAssociationTargetChangeUpdate(SemanticAssociationChange associationChange) {
        return generateDeletePropertyUpdate(associationChange) + generateAddAssociationUpdate(associationChange);
    }

    private List<String> getClassHierarchy(String classIri) {
        var graph = migrationSessionStore.getContext().getUpdatedSchema();
        var model = ModelFactory.createModelForGraph(graph);
        var classResource = model.getResource(classIri);
        var subclasses = CIMClassUtils.findDerivingClasses(classResource);

        return subclasses.stream().map(Resource::getURI).toList();
    }
}

