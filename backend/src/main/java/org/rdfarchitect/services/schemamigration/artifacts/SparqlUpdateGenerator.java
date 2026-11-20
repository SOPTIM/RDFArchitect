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

package org.rdfarchitect.services.schemamigration.artifacts;

import lombok.RequiredArgsConstructor;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.cim.queries.templates.SparqlTemplateLoader;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    public String generateAddAttributeUpdate(
            SemanticAttributeChange attributeChange, String classIri) {
        if (attributeChange.isOptional() && !attributeChange.isForceDefaultValue()) {
            return "";
        }
        if (lacksDefaultValue(attributeChange)) {
            return "";
        }

        var subClasses = getClassHierarchy(classIri);
        var result = new StringBuilder();

        for (var subClass : subClasses) {
            append(result, generateAddAttributeToSingleClassUpdate(attributeChange, subClass));
        }

        return result.toString();
    }

    public String generateAddAttributeToSingleClassUpdate(
            SemanticAttributeChange attributeChange, String classIri) {
        if (attributeChange.isOptional() && !attributeChange.isForceDefaultValue()) {
            return "";
        }
        if (lacksDefaultValue(attributeChange)) {
            return "";
        }

        var xsd =
                TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
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

    public String generateDeletePropertyFromSingleClassUpdate(
            SemanticResourceChange propertyChange, String classIri) {
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
        var result = new StringBuilder();
        for (var oldIri : attributeChange.getAllOldIRIs()) {
            var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-renamed");
            pss.setIri("oldAttribute", oldIri);
            pss.setIri("newAttribute", attributeChange.getIri());
            append(result, pss.toString());
        }
        return result.toString();
    }

    private String generateRenamePropertyUpdate(SemanticPropertyChange propertyChange) {
        return switch (propertyChange) {
            case SemanticAttributeChange attribute -> generateRenameAttributeUpdate(attribute);
            case SemanticAssociationChange association ->
                    generateRenameAssociationUpdate(association);
            case SemanticEnumEntryChange enumEntry -> generateRenameEnumEntryUpdate(enumEntry);
            default ->
                    throw new IllegalStateException(
                            "Cannot rewrite a property of unknown kind: "
                                    + propertyChange.getIri());
        };
    }

    private String deleteFromClassesLosingProperty(
            String oldPropertyIri,
            Collection<String> keepingClasses,
            Map<String, String> renamedClasses) {
        var result = new StringBuilder();

        for (var classIri : classesCarrying(oldPropertyIri)) {
            var renamedTo = renamedClasses.get(classIri);
            if (keepingClasses.contains(renamedTo != null ? renamedTo : classIri)) {
                continue;
            }

            append(result, deletePropertyFromClass(oldPropertyIri, classIri));
            if (renamedTo != null) {
                // the class rename may have retyped the instances before this runs
                append(result, deletePropertyFromClass(oldPropertyIri, renamedTo));
            }
        }
        return result.toString();
    }

    private String deletePropertyFromClass(String propertyIri, String classIri) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/property-deleted");
        pss.setIri("property", propertyIri);
        pss.setIri("domain", classIri);
        return pss.toString();
    }

    private List<String> classesCarrying(String propertyIri) {
        var originalSchema = migrationSessionStore.getContext().getOriginalSchema();
        if (originalSchema == null) {
            return List.of();
        }

        var model = ModelFactory.createModelForGraph(originalSchema);
        var domain = model.getResource(propertyIri).getPropertyResourceValue(RDFS.domain);
        return domain == null ? List.of() : hierarchyOf(domain);
    }

    public String generateMovePropertyUpdate(
            SemanticPropertyChange propertyChange, Map<String, String> renamedClasses) {
        var keepingClasses = getClassHierarchy(propertyChange.getDomain());

        var result = new StringBuilder();
        for (var oldIri : propertyChange.getAllOldIRIs()) {
            append(result, deleteFromClassesLosingProperty(oldIri, keepingClasses, renamedClasses));
        }
        append(result, generateRenamePropertyUpdate(propertyChange));
        return result.toString();
    }

    public <T extends SemanticPropertyChange> String generateSplitPropertyUpdate(
            List<T> siblings, Map<String, String> renamedClasses) {
        var oldProperty = siblings.getFirst().getOldIRI();
        var result = new StringBuilder();
        var keepingClasses = new HashSet<String>();
        for (var sibling : siblings) {
            var targetHierarchy = getClassHierarchy(sibling.getDomain());
            keepingClasses.addAll(targetHierarchy);
            for (var classIri : targetHierarchy) {
                var pss = SparqlTemplateLoader.loadTemplate("migration/property-split");
                pss.setIri("oldProperty", oldProperty);
                pss.setIri("newProperty", sibling.getIri());
                pss.setIri("domain", classIri);
                append(result, pss.toString());
            }
        }
        // a split rewrites per target, so there is no plain rewrite left to carry the rest
        append(
                result,
                deleteFromClassesLosingProperty(oldProperty, keepingClasses, renamedClasses));
        return result.toString();
    }

    public String generateDatatypeChangedUpdate(SemanticAttributeChange attributeChange) {
        if (lacksDefaultValue(attributeChange)) {
            return "";
        }

        var xsd =
                TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-datatype-changed");
        pss.setIri("attribute", attributeChange.getIri());
        pss.setLiteral("defaultValue", attributeChange.getDefaultValue(), xsd);
        pss.setIri("newDatatype", attributeChange.getPrimitiveDataType());
        return pss.toString();
    }

    public String generateEnumDatatypeChangedUpdate(SemanticAttributeChange attributeChange) {
        if (lacksDefaultValue(attributeChange)) {
            return "";
        }

        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-enum-datatype-changed");
        pss.setIri("attribute", attributeChange.getIri());
        pss.setIri("newValue", attributeChange.getDefaultValue());
        return pss.toString();
    }

    public String generateFixedValueUpdate(SemanticAttributeChange attributeChange) {
        if (lacksDefaultValue(attributeChange)) {
            return "";
        }

        var xsd =
                TypeMapper.getInstance().getSafeTypeByName(attributeChange.getPrimitiveDataType());
        var pss = SparqlTemplateLoader.loadTemplate("migration/attribute-fixed-value-changed");
        pss.setIri("attribute", attributeChange.getIri());
        pss.setLiteral("newValue", attributeChange.getDefaultValue(), xsd);
        return pss.toString();
    }

    public String generateRenameEnumEntryUpdate(SemanticEnumEntryChange enumEntryChange) {
        var result = new StringBuilder();
        for (var oldIri : enumEntryChange.getAllOldIRIs()) {
            var pss = SparqlTemplateLoader.loadTemplate("migration/enum-entry-renamed");
            pss.setIri("oldEnumEntry", oldIri);
            pss.setIri("newEnumEntry", enumEntryChange.getIri());
            append(result, pss.toString());
        }
        return result.toString();
    }

    public String generateDeleteEnumEntryUpdate(SemanticEnumEntryChange enumEntryChange) {
        var pss = SparqlTemplateLoader.loadTemplate("migration/enum-entry-deleted");
        pss.setIri("enumEntry", enumEntryChange.getIri());
        pss.setIri("replacementValue", enumEntryChange.getReplacementValue());
        return pss.toString();
    }

    public String generateAddAssociationUpdate(SemanticAssociationChange associationChange) {
        if (!associationChange.isAssociationUsed()
                || associationChange.getMapping() == null
                || associationChange.getMapping().isBlank()) {
            return "";
        }

        var subClasses = getClassHierarchy(associationChange.getMapping());
        var result = new StringBuilder();

        for (var subClass : subClasses) {
            append(result, generateAddAssociationToSingleClassUpdate(associationChange, subClass));
        }

        return result.toString();
    }

    public String generateAddAssociationToSingleClassUpdate(
            SemanticAssociationChange associationChange, String classIri) {
        if (!associationChange.isAssociationUsed()
                || associationChange.getMapping() == null
                || associationChange.getMapping().isBlank()) {
            return "";
        }

        var pss = SparqlTemplateLoader.loadTemplate("migration/association-added");
        pss.setIri("domain", classIri);
        pss.setIri("association", associationChange.getIri());
        var updateString = pss.toString();
        return updateString.replace("<mapping>", associationChange.getMapping());
    }

    public String generateRenameAssociationUpdate(SemanticAssociationChange associationChange) {
        var result = new StringBuilder();
        for (var oldIri : associationChange.getAllOldIRIs()) {
            var pss = SparqlTemplateLoader.loadTemplate("migration/association-renamed");
            pss.setIri("oldAssociation", oldIri);
            pss.setIri("newAssociation", associationChange.getIri());
            append(result, pss.toString());
        }
        return result.toString();
    }

    public String generateAssociationTargetChangeUpdate(
            SemanticAssociationChange associationChange) {
        return generateDeletePropertyUpdate(associationChange)
                + generateAddAssociationUpdate(associationChange);
    }

    /**
     * Appends a single update to a multi-update script. The templates already carry the trailing
     * {@code ;} that separates them, so the blocks only need to be concatenated.
     */
    private void append(StringBuilder script, String update) {
        if (!update.isBlank()) {
            script.append(update);
        }
    }

    private boolean lacksDefaultValue(SemanticAttributeChange attributeChange) {
        return attributeChange.getDefaultValue() == null
                || attributeChange.getDefaultValue().isBlank();
    }

    private List<String> getClassHierarchy(String classIri) {
        var graph = migrationSessionStore.getContext().getUpdatedSchema();
        var model = ModelFactory.createModelForGraph(graph);
        return hierarchyOf(model.getResource(classIri));
    }

    /** The class itself, if it can have instances at all, together with everything deriving. */
    private List<String> hierarchyOf(Resource classResource) {
        var classHierarchy = CIMClassUtils.findDerivingClasses(classResource);
        if (CIMClassUtils.isInstantiableClass(classResource)) {
            classHierarchy.add(classResource);
        }

        return classHierarchy.stream().map(Resource::getURI).toList();
    }
}
