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

package org.rdfarchitect.services.delete;

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.delete.DeleteActions;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindOnDeleteRelationsService implements FindOnDeleteRelationsUseCase {

    private final DatabasePort databasePort;

    @Override
    public AffectedResource getDeleteRelations(GraphIdentifier graphIdentifier, UUID uuid) {
        var model = ModelFactory.createModelForGraph(getCopyOfDatabaseGraph(graphIdentifier));
        var resourceType = CIMResourceTypeIdentifyingUtils.getType(model, uuid);
        var defaultActions = List.of(DeleteActions.DELETE);
        return switch (resourceType) {
            case PACKAGE -> findAffectedRelationsForPackage(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case CLASS -> findAffectedRelationsForClass(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case ATTRIBUTE -> findAffectedRelationsForAttribute(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case ASSOCIATION -> findAffectedRelationsForAssociation(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case ENUM_ENTRY -> findAffectedRelationsForEnumEntry(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case ONTOLOGY -> findAffectedRelationsForOntology(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
            case UNKNOWN -> findAffectedRelationsForUnknown(model, uuid, AffectedResource.AffectedResourceReason.DELETION_REQUESTED_BY_USER, defaultActions);
        };
    }

    private AffectedResource findAffectedRelationsForPackage(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        var classesInPackage = listClassesInPackage(model, uuid);
        var affectedResources = new ArrayList<AffectedResource>();
        var clsDeleteActions = List.of(DeleteActions.DELETE, DeleteActions.KEEP, DeleteActions.REMOVE_REFERENCE);
        for (var cls : classesInPackage) {
            var clsUuid = findUuidForResource(cls);
            var affectedClassResource = findAffectedRelationsForClass(model, clsUuid, AffectedResource.AffectedResourceReason.CONTAINED_IN_PACKAGE, clsDeleteActions);
            affectedResources.add(affectedClassResource);
        }
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.PACKAGE)
                                     .actions(deleteActions)
                                     .reason(reason)
                                     .children(affectedResources);
    }

    private UUID findUuidForResource(Resource resource) {
        if (resource.hasProperty(RDFA.uuid)) {
            throw new IllegalStateException("Resource " + resource + " does not have a UUID.");
        }
        return UUID.fromString(resource.getProperty(RDFA.uuid).getString());
    }

    private List<Resource> listClassesInPackage(Model model, UUID uuid) {
        var packageResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        if (!packageResource.hasProperty(RDF.type, CIMS.classCategory)) {
            throw new IllegalArgumentException("Resource with UUID " + uuid + " is not a package.");
        }
        return model.listSubjectsWithProperty(CIMS.belongsToCategory, packageResource)
                    .filterKeep(cls -> cls.hasProperty(RDF.type, RDFS.Class))
                    .toList();
    }

    private AffectedResource findAffectedRelationsForClass(Model model, UUID uuid,
                                                           AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {

        var childActions = List.of(DeleteActions.DELETE, DeleteActions.KEEP);

        var affectedResources = new ArrayList<AffectedResource>();

        // attributes
        listAttributesWithClassAsDatatype(model, uuid).stream()
                                                      .map(attr -> findAffectedRelationsForAttribute(model,
                                                                                                     findUuidForResource(attr),
                                                                                                     AffectedResource.AffectedResourceReason.USES_DELETE_CLASS_AS_DATATYPE,
                                                                                                     childActions))
                                                      .forEach(affectedResources::add);

        // associations
        listAssociationsReferencingClass(model, uuid).stream()
                                                     .map(assoc -> findAffectedRelationsForAssociation(model,
                                                                                                       findUuidForResource(assoc),
                                                                                                       AffectedResource.AffectedResourceReason.REFENCES_DELETED_CLASS_VIA_ASSOCIATION,
                                                                                                       childActions))
                                                     .forEach(affectedResources::add);

        // Attribute, die Teile der Klasse sind, werden hier nicht abgefragt, da sie immer mitgelöscht werden.
        // (Vllt. sollte man die aber trotzdem mit anzeigen und keine Option oder so geben?)

        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.CLASS)
                                     .actions(deleteActions)
                                     .reason(reason)
                                     .children(affectedResources);
    }

    private List<Resource> listAssociationsReferencingClass(Model model, UUID uuid) {
        var classResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        return model.listSubjectsWithProperty(RDFS.domain, classResource)
                    .filterKeep(CIMPropertyUtils::isAssociation)
                    .toList();
    }

    private List<Resource> listAttributesWithClassAsDatatype(Model model, UUID uuid) {
        var classResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        var byDatatype = model.listSubjectsWithProperty(CIMS.datatype, classResource);
        var byRange = model.listSubjectsWithProperty(RDFS.range, classResource);
        return byDatatype.andThen(byRange)
                         .filterKeep(CIMPropertyUtils::isAttribute)
                         .toList();
    }

    private AffectedResource findAffectedRelationsForAttribute(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.ATTRIBUTE)
                                     .actions(deleteActions)
                                     .reason(reason);
    }

    private AffectedResource findAffectedRelationsForAssociation(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.ASSOCIATION)
                                     .actions(deleteActions)
                                     .reason(reason);
    }

    private AffectedResource findAffectedRelationsForEnumEntry(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        //hier evtl einfügen, dass attrbiute die diese als default wert nutzen gesucht werden, aber noch unklar ob das gemacht wird.
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.ENUM_ENTRY)
                                     .actions(deleteActions)
                                     .reason(reason);
    }

    private AffectedResource findAffectedRelationsForOntology(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        // sollte nichts geben
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.ONTOLOGY)
                                     .actions(deleteActions)
                                     .reason(reason);
    }

    private AffectedResource findAffectedRelationsForUnknown(Model model, UUID uuid, AffectedResource.AffectedResourceReason reason, List<DeleteActions> deleteActions) {
        // ich denke hier entweder einen Fehler werfen oder einfach erlauben die Ressource zu löschen
        return new AffectedResource().uuid(uuid)
                                     .label(getLabelForUuid(model, uuid))
                                     .type(CIMResourceTypeIdentifyingUtils.CimResourceType.UNKNOWN)
                                     .actions(deleteActions)
                                     .reason(reason);
    }

    private Graph getCopyOfDatabaseGraph(GraphIdentifier graphIdentifier) {
        GraphRewindable graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.READ);
            return GraphUtils.deepCopy(graph);
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    private String getLabelForUuid(Model model, UUID uuid) {
        var resource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        if (!resource.hasProperty(RDFS.label)) {
            throw new IllegalStateException("Resource with UUID " + uuid + " does not have a label.");
        }
        return resource.getProperty(RDFS.label).getString();
    }
}
