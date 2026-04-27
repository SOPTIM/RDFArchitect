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

import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.delete.DeleteAction;
import org.rdfarchitect.api.dto.delete.ResourceDeleteRequest;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils.CimResourceType;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeleteResourcesService implements DeleteResourcesUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DeleteResourcesService.class);

    private final DatabasePort databasePort;

    /**
     * Internal record that holds the pre-resolved resource, its CIM type, and the requested action.
     * This avoids redundant model lookups during deletion and allows for upfront validation of
     * unsupported actions.
     */
    private record ResolvedDeleteRequest(
            Resource resource, CimResourceType type, DeleteAction action) {}

    @Override
    public void executeDeleteRequests(
            GraphIdentifier graphIdentifier, List<ResourceDeleteRequest> deleteRequests) {
        GraphRewindable graph = null;
        try {
            graph = databasePort.getGraphWithContext(graphIdentifier).getRdfGraph();
            graph.begin(TxnType.WRITE);
            deleteResources(ModelFactory.createModelForGraph(graph), deleteRequests);
            graph.commit();
        } finally {
            if (graph != null) {
                graph.end();
            }
        }
    }

    private void deleteResources(Model model, List<ResourceDeleteRequest> deleteRequests) {
        var resolvedRequests = resolveAll(model, deleteRequests);
        for (var resolved : resolvedRequests) {
            try {
                deleteResource(resolved);
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                logger.warn(
                        "Skipping deletion of resource {} due to unsupported action: {} : {}",
                        resolved.resource(),
                        resolved.action(),
                        e.getMessage());
            } catch (IllegalStateException e) {
                logger.warn(
                        "Skipping deletion of resource {} due to illegal state: {}",
                        resolved.resource(),
                        e.getMessage());
            }
        }
    }

    /**
     * Resolves all delete requests up front: looks up the resource and its CIM type once per UUID.
     * Requests that cannot be resolved (unknown type, missing resource) are logged and skipped.
     */
    private List<ResolvedDeleteRequest> resolveAll(
            Model model, List<ResourceDeleteRequest> deleteRequests) {
        return deleteRequests.stream()
                .map(req -> resolve(model, req))
                .filter(Objects::nonNull)
                .toList();
    }

    private ResolvedDeleteRequest resolve(Model model, ResourceDeleteRequest req) {
        try {
            var resource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, req.getUuid());
            var type = CIMResourceTypeIdentifyingUtils.getType(model, req.getUuid());
            if (type == CimResourceType.UNKNOWN) {
                logger.warn(
                        "Skipping deletion of resource with UUID {} : unknown resource type",
                        req.getUuid());
                return null;
            }
            return new ResolvedDeleteRequest(resource, type, req.getAction());
        } catch (Exception e) {
            logger.warn(
                    "Skipping deletion of resource with UUID {} : could not resolve: {}",
                    req.getUuid(),
                    e.getMessage());
            return null;
        }
    }

    private void deleteResource(ResolvedDeleteRequest resolved) {
        switch (resolved.type()) {
            case PACKAGE -> deletePackage(resolved);
            case CLASS -> deleteClass(resolved);
            case ATTRIBUTE -> deleteAttribute(resolved);
            case ASSOCIATION -> deleteAssociation(resolved);
            case ENUM_ENTRY -> deleteEnumEntry(resolved);
            case ONTOLOGY -> deleteOntology(resolved);
            case UNKNOWN ->
                    throw new IllegalArgumentException(
                            "Unknown resource type for resource: " + resolved.resource());
        }
    }

    /**
     * Validates the given action against the supported actions for a resource type.
     *
     * @return {@code true} if the action is {@link DeleteAction#KEEP} and should be skipped, {@code
     *     false} if the action is supported and deletion should proceed.
     * @throws IllegalArgumentException if the action is {@code null}
     * @throws UnsupportedOperationException if the action is not in the supported set
     */
    private boolean shouldSkipOrThrow(
            DeleteAction action, CimResourceType type, DeleteAction... supported) {
        if (action == null) {
            throw new IllegalArgumentException("Action must not be null");
        }
        if (action == DeleteAction.KEEP) {
            return true;
        }
        if (!Set.of(supported).contains(action)) {
            throw new UnsupportedOperationException(
                    "Action "
                            + action
                            + " is not supported for "
                            + type.name().toLowerCase().replace("_", " ")
                            + ".");
        }
        return false;
    }

    /**
     * Removes a resource and all its transitively reachable blank nodes from the model. Nodes that
     * are still referenced by other resources are not fully deleted; instead, only their {@code
     * rdfa:uuid} triple is preserved to maintain referential integrity.
     *
     * @param resource the root resource to delete
     */
    private void removeResource(Resource resource) {
        var queue = new ArrayDeque<Resource>();
        queue.add(resource);
        var model = resource.getModel();

        while (!queue.isEmpty()) {
            var current = queue.poll();
            current.listProperties().toList().stream()
                    .filter(stmt -> stmt.getObject().isAnon())
                    .forEach(stmt -> queue.add(stmt.getObject().asResource()));

            // Delete inverse association
            var inverseStmt = current.getProperty(CIMS.inverseRoleName);
            if (inverseStmt != null && inverseStmt.getObject().isResource()) {
                var inverse = inverseStmt.getObject().asResource();
                model.remove(inverse, CIMS.inverseRoleName, current);
                queue.add(inverse);
            }

            if (isReferencedElsewhere(model, current)) {
                var uuidStmt = current.getProperty(RDFA.uuid);
                current.listProperties().forEach(model::remove);
                if (uuidStmt != null) {
                    model.add(uuidStmt);
                }
            } else {
                current.listProperties().forEach(model::remove);
            }
        }
    }

    private boolean isReferencedElsewhere(Model model, Resource resource) {
        return model.listStatements(null, null, resource)
                .filterDrop(stmt -> stmt.getPredicate().equals(RDFA.uuid))
                .hasNext();
    }

    private void deletePackage(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(resolved.action(), CimResourceType.PACKAGE, DeleteAction.DELETE)) {
            return;
        }
        removeResource(resolved.resource());
    }

    /**
     * Deletes a class and all its owned resources (attributes, associations, and enum entries). If
     * the action is {@link DeleteAction#REMOVE_SUBCLASS_REFERENCE}, only the {@code
     * rdfs:subClassOf} triple is removed, leaving the class itself intact.
     */
    private void deleteClass(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(
                resolved.action(),
                CimResourceType.CLASS,
                DeleteAction.DELETE,
                DeleteAction.REMOVE_SUBCLASS_REFERENCE,
                DeleteAction.REMOVE_PACKAGE_REFERENCE)) {
            return;
        }
        var resource = resolved.resource();
        var model = resource.getModel();

        if (resolved.action() == DeleteAction.REMOVE_SUBCLASS_REFERENCE) {
            resource.listProperties(RDFS.subClassOf).forEach(model::remove);
            return;
        }

        if (resolved.action() == DeleteAction.REMOVE_PACKAGE_REFERENCE) {
            resource.listProperties(CIMS.belongsToCategory).forEach(model::remove);
            return;
        }

        // Delete attributes
        model.listSubjectsWithProperty(RDFS.domain, resource)
                .filterKeep(CIMPropertyUtils::isAttribute)
                .toList()
                .forEach(this::removeResource);

        // Delete associations only if they reference an external resource
        model.listSubjectsWithProperty(RDFS.domain, resource)
                .filterKeep(CIMPropertyUtils::isAssociation)
                .filterKeep(
                        assoc ->
                                CIMResourceUtils.isExternalResource(
                                        assoc.getProperty(RDFS.range).getObject().asResource()))
                .toList()
                .forEach(this::removeResource);

        // Delete enum entries
        model.listSubjectsWithProperty(RDF.type, resource)
                .filterKeep(CIMResourceTypeIdentifyingUtils::isEnumEntry)
                .toList()
                .forEach(this::removeResource);

        removeResource(resource);
    }

    private void deleteAttribute(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(resolved.action(), CimResourceType.ATTRIBUTE, DeleteAction.DELETE)) {
            return;
        }
        removeResource(resolved.resource());
    }

    /**
     * Deletes an association and its inverse. Since associations reference each other via {@code
     * cims:inverseRoleName}, the mutual references are removed first to prevent {@link
     * #removeResource} from preserving stale UUID triples due to the circular reference.
     */
    private void deleteAssociation(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(
                resolved.action(), CimResourceType.ASSOCIATION, DeleteAction.DELETE)) {
            return;
        }
        var resource = resolved.resource();
        var model = resource.getModel();

        var inverseStmt = resource.getProperty(CIMS.inverseRoleName);
        if (inverseStmt != null && inverseStmt.getObject().isResource()) {
            var inverse = inverseStmt.getObject().asResource();
            // Break circular reference before deleting
            model.remove(inverse, CIMS.inverseRoleName, resource);
            model.remove(resource, CIMS.inverseRoleName, inverse);
            removeResource(inverse);
        }

        removeResource(resource);
    }

    private void deleteEnumEntry(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(resolved.action(), CimResourceType.ENUM_ENTRY, DeleteAction.DELETE)) {
            return;
        }
        removeResource(resolved.resource());
    }

    private void deleteOntology(ResolvedDeleteRequest resolved) {
        if (shouldSkipOrThrow(resolved.action(), CimResourceType.ONTOLOGY, DeleteAction.DELETE)) {
            return;
        }
        removeResource(resolved.resource());
    }
}
