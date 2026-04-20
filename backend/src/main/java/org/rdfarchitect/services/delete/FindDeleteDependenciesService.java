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
import org.rdfarchitect.api.dto.delete.DeleteAction;
import org.rdfarchitect.api.dto.delete.ResourceIdentifier;
import org.rdfarchitect.api.dto.delete.relations.AffectedAssociation;
import org.rdfarchitect.api.dto.delete.relations.AffectedOwnedResource;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.api.dto.delete.relations.AffectedResource.AffectedResourceReason;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils.CimResourceType;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindDeleteDependenciesService implements FindDeleteDependenciesUseCase {

    private final DatabasePort databasePort;

    @Override
    public AffectedResource getDeleteDependencies(GraphIdentifier graphIdentifier, UUID uuid) {
        var model = ModelFactory.createModelForGraph(getCopyOfDatabaseGraph(graphIdentifier));
        var resourceType = CIMResourceTypeIdentifyingUtils.getType(model, uuid);
        var defaultActions = List.of(DeleteAction.DELETE);
        var reason = AffectedResourceReason.DELETION_REQUESTED_BY_USER;
        return switch (resourceType) {
            case PACKAGE -> findAffectedRelationsForPackage(model, uuid, reason, defaultActions);
            case CLASS -> findAffectedRelationsForClass(model, uuid, reason, defaultActions);
            case ATTRIBUTE -> new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.ATTRIBUTE, reason)
                      .setActions(defaultActions);
            case ASSOCIATION -> new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.ASSOCIATION, reason)
                      .setActions(defaultActions);
            case ENUM_ENTRY -> new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.ENUM_ENTRY, reason)
                      .setActions(defaultActions);
            case ONTOLOGY -> new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.ONTOLOGY, reason)
                      .setActions(defaultActions);
            case UNKNOWN -> new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.UNKNOWN, reason)
                      .setActions(defaultActions);
        };
    }

    private AffectedResource findAffectedRelationsForPackage(Model model, UUID uuid, AffectedResourceReason reason, List<DeleteAction> deleteActions) {
        var classesInPackage = listClassesInPackage(model, uuid);
        var affectedResources = new ArrayList<AffectedResource>();
        var clsDeleteActions = List.of(DeleteAction.DELETE, DeleteAction.KEEP, DeleteAction.REMOVE_PACKAGE_REFERENCE);
        for (var cls : classesInPackage) {
            var clsUuid = CIMResourceUtils.findUuidForResource(cls);
            var affectedClassResource = findAffectedRelationsForClass(model, clsUuid, AffectedResourceReason.CONTAINED_IN_PACKAGE, clsDeleteActions);
            affectedResources.add(affectedClassResource);
        }
        return new AffectedResource(createResourceIdentifier(model, uuid), CimResourceType.PACKAGE, reason)
                  .setActions(deleteActions)
                  .setChildren(affectedResources);
    }

    private AffectedResource findAffectedRelationsForClass(Model model, UUID uuid,
                                                           AffectedResourceReason reason, List<DeleteAction> deleteActions) {
        var classResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);

        var classResourceId = createResourceIdentifier(model, uuid);
        var affectedResources = new ArrayList<AffectedResource>();
        affectedResources.addAll(findAffectedAttributesForClass(classResource));
        affectedResources.addAll(findAffectedAssociationsForClass(classResource, classResourceId));
        affectedResources.addAll(findAffectedChildClassesForClass(classResource));

        return new AffectedResource(classResourceId, CimResourceType.CLASS, reason)
                  .setActions(deleteActions)
                  .setChildren(affectedResources);
    }

    private List<AffectedResource> findAffectedAttributesForClass(Resource classResource) {
        var childActions = List.of(DeleteAction.DELETE, DeleteAction.KEEP);
        return listAttributesWithClassAsDatatype(classResource).stream()
                                                               .map(attr -> new AffectedOwnedResource(createResourceIdentifier(attr),
                                                                                                      CimResourceType.ATTRIBUTE,
                                                                                                      AffectedResourceReason.USES_DELETED_CLASS_AS_DATATYPE,
                                                                                                      createResourceIdentifier(attr.getProperty(RDFS.domain).getObject().asResource()))
                                                                         .setActions(childActions))
                                                               .toList();
    }

    private List<AffectedResource> findAffectedAssociationsForClass(Resource classResource, ResourceIdentifier classResourceId) {
        return listAssociationsReferencingClass(classResource).stream()
                                                              .map(assoc -> {
                                                                  var childActions = new ArrayList<DeleteAction>();
                                                                  childActions.add(DeleteAction.DELETE);
                                                                  if(!CIMResourceUtils.isExternalResource(assoc.getProperty(RDFS.range).getObject().asResource())){
                                                                      childActions.add(DeleteAction.KEEP);
                                                                  }
                                                                  return new AffectedAssociation(createResourceIdentifier(assoc),
                                                                                                 CimResourceType.ASSOCIATION,
                                                                                                 AffectedResourceReason.REFENCES_DELETED_CLASS_VIA_ASSOCIATION,
                                                                                                 classResourceId,
                                                                                                 getAssociationTarget(assoc)
                                                                  )
                                                                            .setActions(childActions);
                                                              })
                                                              .toList();
    }

    private ResourceIdentifier getAssociationTarget(Resource associationResource) {
        var rangeStatement = associationResource.getProperty(RDFS.range);
        if (rangeStatement == null) {
            throw new IllegalStateException("Association " + associationResource + " does not have a range.");
        }
        if (rangeStatement.getObject().isLiteral()) {
            throw new IllegalStateException("Association " + associationResource + " has a literal as range, which is not supported.");
        }
        var rangeResource = rangeStatement.getObject().asResource();
        return createResourceIdentifier(rangeResource);
    }

    private List<AffectedResource> findAffectedChildClassesForClass(Resource classResource) {
        var childClassActions = List.of(DeleteAction.DELETE, DeleteAction.KEEP, DeleteAction.REMOVE_SUBCLASS_REFERENCE);
        return buildAffectedChildClassTree(classResource, childClassActions);
    }

    private List<AffectedResource> buildAffectedChildClassTree(Resource classResource, List<DeleteAction> childClassActions) {
        var visited = new HashSet<Resource>();
        visited.add(classResource);

        var resourceMap = new HashMap<Resource, AffectedResource>();
        var rootChildren = new ArrayList<AffectedResource>();

        var queue = initializeQueue(classResource, visited);

        while (!queue.isEmpty()) {
            var entry = queue.poll();
            var current = entry.getKey();
            var parent = entry.getValue();

            var affectedResource = createAffectedChildClass(current, childClassActions);
            resourceMap.put(current, affectedResource);

            attachToParentOrRoot(affectedResource, parent, classResource, resourceMap, rootChildren);
            enqueueChildren(current, visited, queue);
        }

        return rootChildren;
    }

    private LinkedList<Map.Entry<Resource, Resource>> initializeQueue(Resource classResource, Set<Resource> visited) {
        var queue = new LinkedList<Map.Entry<Resource, Resource>>();
        for (var directChild : listDirectlyDescendingClasses(classResource)) {
            if (visited.add(directChild)) {
                queue.add(Map.entry(directChild, classResource));
            }
        }
        return queue;
    }

    private AffectedResource createAffectedChildClass(Resource classResource, List<DeleteAction> actions) {
        var resourceId = createResourceIdentifier(classResource);
        var children = new ArrayList<AffectedResource>();
        children.addAll(findAffectedAttributesForClass(classResource));
        children.addAll(findAffectedAssociationsForClass(classResource, resourceId));

        return new AffectedResource(resourceId, CimResourceType.CLASS, AffectedResourceReason.CHILD_OF)
                  .setActions(actions)
                  .setChildren(children);
    }

    private void attachToParentOrRoot(AffectedResource affectedResource, Resource parent,
                                      Resource rootClass, Map<Resource, AffectedResource> resourceMap,
                                      List<AffectedResource> rootChildren) {
        if (parent.equals(rootClass)) {
            rootChildren.add(affectedResource);
            return;
        }
        var parentAffected = resourceMap.get(parent);
        if (parentAffected != null) {
            var existingChildren = parentAffected.getChildren();
            if (existingChildren == null) {
                existingChildren = new ArrayList<>();
            }
            existingChildren.add(affectedResource);
            parentAffected.setChildren(existingChildren);
        }
    }

    private void enqueueChildren(Resource current, Set<Resource> visited,
                                 LinkedList<Map.Entry<Resource, Resource>> queue) {
        for (var child : listDirectlyDescendingClasses(current)) {
            if (visited.add(child)) {
                queue.add(Map.entry(child, current));
            }
        }
    }

    private List<Resource> listDirectlyDescendingClasses(Resource classResource) {
        return classResource.getModel().listSubjectsWithProperty(RDFS.subClassOf, classResource).toList();
    }

    private List<Resource> listClassesInPackage(Model model, UUID uuid) {
        var packageResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        if (!packageResource.hasProperty(RDF.type, CIMS.classCategory)) {
            throw new IllegalStateException("Resource with UUID " + uuid + " is not a package.");
        }
        return model.listSubjectsWithProperty(CIMS.belongsToCategory, packageResource)
                    .filterKeep(cls -> cls.hasProperty(RDF.type, RDFS.Class))
                    .toList();
    }

    private List<Resource> listAssociationsReferencingClass(Resource classResource) {
        return classResource.getModel().listSubjectsWithProperty(RDFS.domain, classResource)
                            .filterKeep(CIMPropertyUtils::isAssociation)
                            .toList();
    }

    private List<Resource> listAttributesWithClassAsDatatype(Resource classResource) {
        var model = classResource.getModel();
        var byDatatype = model.listSubjectsWithProperty(CIMS.datatype, classResource);
        var byRange = model.listSubjectsWithProperty(RDFS.range, classResource);
        return byDatatype.andThen(byRange)
                         .filterKeep(CIMPropertyUtils::isAttribute)
                         .toList().stream().distinct().toList();
    }

    private ResourceIdentifier createResourceIdentifier(Model model, UUID uuid) {
        var resource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, uuid);
        return createResourceIdentifier(resource);
    }

    private ResourceIdentifier createResourceIdentifier(Resource resource) {
        var uuid = CIMResourceUtils.findUuidForResource(resource);
        var label = resource.getLocalName();
        if (resource.hasProperty(RDFS.label)) {
            label = resource.getProperty(RDFS.label).getString();
        }
        return new ResourceIdentifier().setUuid(uuid)
                                       .setLabel(label)
                                       .setNamespace(resource.getNameSpace());
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
}