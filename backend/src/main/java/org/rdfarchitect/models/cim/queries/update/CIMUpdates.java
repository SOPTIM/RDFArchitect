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

package org.rdfarchitect.models.cim.queries.update;

import lombok.experimental.UtilityClass;
import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.search.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.search.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.search.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.search.data.dto.CIMClass;
import org.rdfarchitect.models.cim.search.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.search.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.search.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.search.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;
import org.rdfarchitect.database.inmemory.SessionDataStore;
import org.rdfarchitect.rdf.RDFUtils;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class CIMUpdates {

    private static final String ANY_1 = "?any1";
    private static final String ANY_2 = "?any2";
    private static final String ANY_3 = "?any3";
    private static final String ANY_4 = "?any4";

    /**
     * replaces a class in a {@link Graph} with a new {@link CIMClassUMLAdapted}
     *
     * @param graph         The graph to replace the class in.
     * @param prefixMapping The {@link PrefixMapping} of the graph.
     * @param newClass      The new {@link CIMClassUMLAdapted} to replace.
     */
    public void replaceClass(Graph graph, PrefixMapping prefixMapping, CIMClassUMLAdapted newClass) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        //replace attributes in database
        var updateAttributes = replaceAttributes(prefixMapping, null, newClass.getUuid().toString(), newClass.getAttributes());
        UpdateExecutionFactory.create(updateAttributes.build(), dataset).execute();
        //replace associations in database
        var updateAssociations = replaceAssociations(prefixMapping, null, newClass.getUuid().toString(), newClass.getAssociationPairs());
        UpdateExecutionFactory.create(updateAssociations.build(), dataset).execute();
        //replace enum entries in database
        var updateEnumEntries = replaceEnumEntries(prefixMapping, null, newClass.getUuid().toString(), newClass.getEnumEntries());
        UpdateExecutionFactory.create(updateEnumEntries.build(), dataset).execute();
        //update other references to this class
        updateReferences(graph, prefixMapping, newClass.getUuid(), newClass.getUri());
        //replace classObject in database
        var updateClassBase = replaceClassBase(prefixMapping, null, newClass);
        UpdateExecutionFactory.create(updateClassBase.build(), dataset).execute();
    }

    public UUID insertClass(Graph graph, PrefixMapping prefixMapping, CIMClass newClass) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        var uuid = UUID.randomUUID();
        var model = ModelFactory.createModelForGraph(graph);
        var existingResource = model.getResource(newClass.getUri().toString());
        if (existingResource != null && existingResource.hasProperty(RDFA.uuid)) {
            var existingUUIDLiteral = existingResource.getProperty(RDFA.uuid).getObject();
            if (existingUUIDLiteral != null) {
                uuid = UUID.fromString(existingUUIDLiteral.asLiteral().getString());
            }
        }
        newClass.setUuid(uuid);
        var insertClass = insertClass(prefixMapping, null, newClass);
        UpdateExecutionFactory.create(insertClass.build(), dataset).execute();
        return uuid;
    }

    private UpdateBuilder insertClass(PrefixMapping prefixMapping, String graphURI, CIMClass newClass) {
        var newClassURI = newClass.getUri().toNode();
        var classBaseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build();
        classBaseUpdate
                  .addInsert(newClassURI, RDF.type, RDFS.Class)
                  .addInsert(newClassURI, RDFS.label, newClass.getLabel().asLangLiteral());
        if (newClass.getUuid() != null) {
            classBaseUpdate.addInsert(newClassURI, RDFA.uuid, newClass.getUuid().toString());
        }
        if (newClass.getSuperClass() != null) {
            classBaseUpdate.addInsert(newClassURI, RDFS.subClassOf, newClass.getSuperClass().getUri().toNode());
        }
        if (newClass.getComment() != null) {
            classBaseUpdate.addInsert(newClassURI, RDFS.comment, newClass.getComment().asTypedLiteral());
        }
        if (newClass.getBelongsToCategory() != null) {
            classBaseUpdate.addInsert(newClassURI, CIMS.belongsToCategory, newClass.getBelongsToCategory().getUri().toNode());
        }
        for (CIMSStereotype stereotype : newClass.getStereotypes()) {
            classBaseUpdate.addInsert(newClassURI, CIMS.stereotype, RDFUtils.wrapURLorLiteral(stereotype.getStereotype()));
        }
        return classBaseUpdate;
    }

    public void deleteClass(Graph graph, PrefixMapping prefixMapping, String classUUID) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        var deleteAttributes = deleteAttributes(prefixMapping, null, classUUID);
        UpdateExecutionFactory.create(deleteAttributes.build(), dataset).execute();
        var deleteEnumEntries = deleteEnumEntries(prefixMapping, null, classUUID);
        UpdateExecutionFactory.create(deleteEnumEntries.build(), dataset).execute();
        var classBaseDelete = deleteBase(prefixMapping, null, classUUID);
        UpdateExecutionFactory.create(classBaseDelete.build(), dataset).execute();

        deleteUuidIfNotReferencedAnyWhereElse(graph, UUID.fromString(classUUID));
    }

    public UpdateBuilder deleteAttribute(PrefixMapping prefixMapping, String graphURI, UUID attributeUUID) {
        return new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addWhere(CIMQueryVars.URI, RDFA.uuid, attributeUUID.toString())
                  .addDelete(CIMQueryVars.URI, "?pre", "?obj")
                  .addWhere(CIMQueryVars.URI, "?pre", "?obj");
    }

    public UpdateBuilder insertAttribute(PrefixMapping prefixMapping, String graphURI, CIMAttribute attribute) {
        var baseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addOptional("?sub", "?pre", "?obj");

        return appendInsertAttribute(baseUpdate, attribute);
    }

    public UpdateBuilder replaceAttribute(PrefixMapping prefixMapping, String graphURI, CIMAttribute attribute) {
        var baseUpdate = deleteAttribute(prefixMapping, graphURI, attribute.getUuid());
        return appendInsertAttribute(baseUpdate, attribute);
    }

    private UpdateBuilder appendInsertAttribute(UpdateBuilder baseUpdate, CIMAttribute attribute) {
        var newURI = attribute.getUri().toNode();

        var label = attribute.getLabel().asLangLiteral();
        baseUpdate.addInsert(newURI, RDFA.uuid, attribute.getUuid().toString());
        baseUpdate.addInsert(newURI, RDFS.label, label);
        baseUpdate.addInsert(newURI, CIMS.multiplicity, attribute.getMultiplicity().getUri().toNode());
        baseUpdate.addInsert(newURI, RDF.type, RDF.Property);
        baseUpdate.addInsert(newURI, RDFS.domain, attribute.getDomain().getUri().toNode());
        baseUpdate.addInsert(newURI, CIMS.stereotype, CIMStereotypes.attribute);
        //range/datatype
        if (attribute.getDataType().getType() == CIMSDataType.Type.PRIMITIVE) {
            baseUpdate.addInsert(newURI, CIMS.datatype, attribute.getDataType().getUri().toNode());
        } else {
            baseUpdate.addInsert(newURI, RDFS.range, attribute.getDataType().getUri().toNode());
        }
        //comment
        if (attribute.getComment() != null) {
            baseUpdate.addInsert(newURI, RDFS.comment, attribute.getComment().asTypedLiteral());
        }
        //isFixed
        if (attribute.getFixedValue() != null) {
            baseUpdate.addInsert(newURI, CIMS.isFixed, attribute.getFixedValue().asLiteral());
        }
        //isDefault
        if (attribute.getDefaultValue() != null) {
            baseUpdate.addInsert(newURI, CIMS.isDefault, attribute.getDefaultValue().asLiteral());
        }
        return baseUpdate;
    }

    private UpdateBuilder deleteAttributes(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        return new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addDelete(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addWhere(CIMQueryVars.DOMAIN_URI, RDFA.uuid, classUUID)
                  .addWhere(CIMQueryVars.URI, RDFS.domain, CIMQueryVars.DOMAIN_URI)
                  .addWhere(CIMQueryVars.URI, CIMS.stereotype, CIMStereotypes.attribute)
                  .addWhere(CIMQueryVars.URI, ANY_1, ANY_2);
    }

    public UpdateBuilder replaceAttributes(PrefixMapping prefixMapping, String graphURI, String classUUID, List<CIMAttribute> attributes) {
        var baseUpdate = CIMUpdates.deleteAttributes(prefixMapping, graphURI, classUUID);
        for (CIMAttribute attribute : attributes) {
            appendInsertAttribute(baseUpdate, attribute);
        }
        return baseUpdate;
    }

    public UpdateBuilder deleteAssociation(PrefixMapping prefixMapping, String graphURI, UUID fromAssociationUUID) {
        //init baseUpdate
        var baseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build();

        return baseUpdate
                  .addDelete(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addDelete(CIMQueryVars.Inverse.URI, ANY_3, ANY_4)
                  .addWhere(CIMQueryVars.URI, RDFA.uuid, fromAssociationUUID.toString())
                  .addWhere(CIMQueryVars.URI, CIMS.inverseRoleName, CIMQueryVars.Inverse.URI)
                  .addWhere(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addWhere(CIMQueryVars.Inverse.URI, ANY_3, ANY_4);
    }

    public UpdateBuilder insertAssociation(PrefixMapping prefixMapping, String graphURI, CIMAssociationPair associationPair) {
        var baseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addOptional("?sub", "?pre", "?obj");

        return appendInsertAssociationPair(baseUpdate, associationPair);
    }

    public UpdateBuilder replaceAssociation(PrefixMapping prefixMapping, String graphURI, CIMAssociationPair associationPair) {
        var baseUpdate = deleteAssociation(prefixMapping, graphURI, associationPair.getFrom().getUuid());

        return appendInsertAssociationPair(baseUpdate, associationPair);
    }

    private UpdateBuilder appendInsertAssociationPair(UpdateBuilder baseUpdate, CIMAssociationPair associationPair) {
        var from = associationPair.getFrom();
        var to = associationPair.getTo();
        appendInsertAssociation(baseUpdate, from);
        appendInsertAssociation(baseUpdate, to);
        return baseUpdate;
    }

    private void appendInsertAssociation(UpdateBuilder baseUpdate, CIMAssociation association) {
        baseUpdate
                  .addInsert(association.getUri().toNode(), RDF.type, RDF.Property)
                  .addInsert(association.getUri().toNode(), RDFA.uuid, association.getUuid().toString())
                  .addInsert(association.getUri().toNode(), RDFS.label, association.getLabel().asLangLiteral())
                  .addInsert(association.getUri().toNode(), RDFS.domain, association.getDomain().getUri().toNode())
                  .addInsert(association.getUri().toNode(), RDFS.range, association.getRange().getUri().toNode())
                  .addInsert(association.getUri().toNode(), CIMS.associationUsed, "\"" + association.getAssociationUsed().toString() + "\"")
                  .addInsert(association.getUri().toNode(), CIMS.inverseRoleName, association.getInverseRoleName().getUri().toNode())
                  .addInsert(association.getUri().toNode(), CIMS.multiplicity, association.getMultiplicity().getUri().toNode());
        if (association.getComment() != null) {
            baseUpdate.addInsert(association.getUri().toNode(), RDFS.comment, association.getComment().asTypedLiteral());
        }
    }

    private UpdateBuilder deleteAssociations(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        //init baseUpdate
        var baseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build();
        return baseUpdate
                  .addDelete(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addDelete(CIMQueryVars.Inverse.URI, ANY_3, ANY_4)
                  .addWhere(CIMQueryVars.DOMAIN_URI, RDFA.uuid, classUUID)
                  .addWhere(CIMQueryVars.URI, RDFS.domain, CIMQueryVars.DOMAIN_URI)
                  .addWhere(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addWhere(CIMQueryVars.URI, CIMS.inverseRoleName, CIMQueryVars.Inverse.URI)
                  .addWhere(CIMQueryVars.Inverse.URI, ANY_3, ANY_4);
    }

    public UpdateBuilder replaceAssociations(PrefixMapping prefixMapping, String graphURI, String classUUID, List<CIMAssociationPair> associationPairs) {
        var baseUpdate = CIMUpdates.deleteAssociations(prefixMapping, graphURI, classUUID);
        for (CIMAssociationPair associationPair : associationPairs) {
            appendInsertAssociationPair(baseUpdate, associationPair);
        }
        return baseUpdate;
    }

    public UpdateBuilder deleteAttributesOfType(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        return new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addWhere(CIMQueryVars.RANGE_URI, RDFA.uuid, classUUID)
                  .addDelete(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addWhere(CIMQueryVars.URI, RDFS.range, CIMQueryVars.RANGE_URI)
                  .addWhere(CIMQueryVars.URI, ANY_1, ANY_2);
    }

    public UpdateBuilder replaceClassBase(PrefixMapping prefixMapping, String graphURI, CIMClass newClass) {
        return insertClass(prefixMapping, graphURI, newClass)
                  .addDelete(CIMQueryVars.URI, "?pre", "?obj")
                  .addWhere(CIMQueryVars.URI, RDFA.uuid, newClass.getUuid().toString())
                  .addOptional(CIMQueryVars.URI, "?pre", "?obj");
    }

    public UpdateBuilder deleteBase(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        var classBaseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build();
        classBaseUpdate
                  .addDelete(CIMQueryVars.URI, "?pre", "?obj")
                  .addWhere(CIMQueryVars.URI, RDFA.uuid, classUUID)
                  .addOptional(CIMQueryVars.URI, "?pre", "?obj")
                  .addFilter(new ExprFactory().ne("?pre", RDFA.uuid));

        return classBaseUpdate;
    }

    private UpdateBuilder insertEnumEntry(PrefixMapping prefixMapping, String graphURI, CIMEnumEntry newEnumEntry) {
        var classBaseUpdate = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build();
        appendInsertEnumEntry(classBaseUpdate, newEnumEntry);
        return classBaseUpdate;
    }

    private void appendInsertEnumEntry(UpdateBuilder baseUpdate, CIMEnumEntry newEnumEntry) {
        var newEnumEntryURI = NodeFactory.createURI(newEnumEntry.getUri().toString());
        baseUpdate
                  .addInsert(newEnumEntryURI, RDF.type, newEnumEntry.getType().getUri().toNode())
                  .addInsert(newEnumEntryURI, RDFS.label, newEnumEntry.getLabel().asLangLiteral());
        if (newEnumEntry.getUuid() != null) {
            baseUpdate.addInsert(newEnumEntryURI, RDFA.uuid, newEnumEntry.getUuid().toString());
        }
        if (newEnumEntry.getComment() != null) {
            baseUpdate.addInsert(newEnumEntryURI, RDFS.comment, newEnumEntry.getComment().asTypedLiteral());
        }
        if (newEnumEntry.getStereotype() != null) {
            baseUpdate.addInsert(newEnumEntryURI, CIMS.stereotype, CIMStereotypes.enumLiteral);
        }
    }

    public void insertEnumEntry(Graph graph, PrefixMapping prefixMapping, CIMEnumEntry newEnumEntry) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);
        var insertEnumEntry = insertEnumEntry(prefixMapping, null, newEnumEntry);
        UpdateExecutionFactory.create(insertEnumEntry.build(), dataset).execute();
    }

    public void deleteEnumEntry(Graph graph, PrefixMapping prefixMapping, String enumEntryUUID) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);

        var deleteEnumEntry = deleteBase(prefixMapping, null, enumEntryUUID);
        UpdateExecutionFactory.create(deleteEnumEntry.build(), dataset).execute();
        deleteUuidIfNotReferencedAnyWhereElse(graph, UUID.fromString(enumEntryUUID));
    }

    private UpdateBuilder deleteEnumEntries(PrefixMapping prefixMapping, String graphURI, String classUUID) {
        return new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(graphURI)
                  .build()
                  .addDelete(CIMQueryVars.URI, ANY_1, ANY_2)
                  .addWhere(CIMQueryVars.TYPE_URI, RDFA.uuid, classUUID)
                  .addWhere(CIMQueryVars.URI, RDF.type, CIMQueryVars.TYPE_URI)
                  .addWhere(CIMQueryVars.URI, ANY_1, ANY_2);
    }

    public UpdateBuilder replaceEnumEntries(PrefixMapping prefixMapping, String graphURI, String classUUID, List<CIMEnumEntry> enumEntries) {
        var baseUpdate = CIMUpdates.deleteEnumEntries(prefixMapping, graphURI, classUUID);
        for (CIMEnumEntry enumEntry : enumEntries) {
            appendInsertEnumEntry(baseUpdate, enumEntry);
        }
        return baseUpdate;
    }

    public void replaceEnumEntry(Graph graph, PrefixMapping prefixMapping, CIMEnumEntry enumEntry) {
        deleteEnumEntry(graph, prefixMapping, enumEntry.getUuid().toString());
        insertEnumEntry(graph, prefixMapping, enumEntry);
    }

    public void replacePackage(Graph graph, PrefixMapping prefixMapping, CIMPackage newPackage) {
        //update belongsToCategory references from classes
        updateReferences(graph, prefixMapping, newPackage.getUuid(), newPackage.getUri());

        //delete old package
        deletePackage(graph, prefixMapping, newPackage.getUuid().toString());

        //insert new package
        insertPackage(graph, prefixMapping, newPackage);
    }

    public void insertPackage(Graph graph, PrefixMapping prefixMapping, CIMPackage newPackage) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);

        var newPackageURI = newPackage.getUri().toNode();
        var packageUpdateBuilder = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(null)
                  .build();
        packageUpdateBuilder
                  .addInsert(newPackageURI, RDF.type, CIMS.classCategory)
                  .addInsert(newPackageURI, RDFS.label, newPackage.getLabel().asLangLiteral());
        if (newPackage.getUuid() != null) {
            packageUpdateBuilder.addInsert(newPackageURI, RDFA.uuid, newPackage.getUuid().toString());
        }
        if (newPackage.getComment() != null) {
            packageUpdateBuilder.addInsert(newPackageURI, RDFS.comment, newPackage.getComment().asTypedLiteral());
        }

        UpdateExecutionFactory.create(packageUpdateBuilder.build(), dataset).execute();
    }

    public void deletePackage(Graph graph, PrefixMapping prefixMapping, String packageUUID) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);

        var packageBaseDelete = deleteBase(prefixMapping, null, packageUUID);
        UpdateExecutionFactory.create(packageBaseDelete.build(), dataset).execute();
        deleteUuidIfNotReferencedAnyWhereElse(graph, UUID.fromString(packageUUID));
    }

    private void updateReferences(Graph graph, PrefixMapping prefixMapping, UUID uuid, URI newURI) {
        var dataset = SessionDataStore.wrapGraphInDataset(graph, null);

        var updateReferencesToThis = new CIMBaseUpdateBuilder()
                  .addPrefixes(prefixMapping)
                  .setGraph(null)
                  .build()
                  .addDelete(CIMQueryVars.URI, "?pre", "?ref")
                  .addWhere("?ref", RDFA.uuid, uuid.toString())
                  .addWhere(CIMQueryVars.URI, "?pre", "?ref")
                  .addInsert(CIMQueryVars.URI, "?pre", newURI.toNode());
        UpdateExecutionFactory.create(updateReferencesToThis.build(), dataset).execute();
    }

    private void deleteUuidIfNotReferencedAnyWhereElse(Graph graph, UUID uuid) {
        var model = ModelFactory.createModelForGraph(graph);
        var uuidLiteral = model.createLiteral(uuid.toString());

        var resource = model.listStatements(null, RDFA.uuid, uuidLiteral)
                            .nextOptional()
                            .map(Statement::getSubject)
                            .orElse(null);

        if (resource == null) {
            return;
        }

        var isReferencedElsewhere = model.listStatements(null, null, resource).hasNext();

        if (!isReferencedElsewhere) {
            model.removeAll(resource, RDFA.uuid, uuidLiteral);
        }
    }
}
