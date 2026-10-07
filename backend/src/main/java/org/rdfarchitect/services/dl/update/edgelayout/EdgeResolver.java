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

package org.rdfarchitect.services.dl.update.edgelayout;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Derives from the schema which edges connect the classes of a diagram. Mirrors the edges the
 * svelteflow renderer draws: an inheritance edge to every super class and an association edge for
 * every renderable association whose range is in the diagram as well.
 */
@FunctionalInterface
public interface EdgeResolver {

    /**
     * Resolves the edges between the given classes.
     *
     * @param classUUIDs the UUIDs the classes of a diagram are identified by in the diagram layout
     * @return the name of the edge diagram object for every edge connecting two of the classes
     */
    Map<EdgeKey, String> edgesBetween(Set<UUID> classUUIDs);

    /**
     * Resolves edges of a diagram whose classes are identified by the UUIDs they carry in a single
     * graph, i.e. package diagrams and the custom diagrams of a graph.
     *
     * @param model the graph the diagram belongs to
     * @return the resolver
     */
    static EdgeResolver forGraph(ICIMModelFacade model) {
        return classUUIDs -> {
            var edges = new LinkedHashMap<EdgeKey, String>();
            for (var classUUID : classUUIDs) {
                var cimClass = model.getCIMClass(classUUID);
                if (cimClass != null) {
                    addEdgesOf(cimClass, classUUID, classUUIDs, ICIMResource::getUuid, edges);
                }
            }
            return edges;
        };
    }

    /**
     * Resolves edges of a diagram of a single graph, see {@link #forGraph(ICIMModelFacade)}.
     *
     * @param graphUri the URI of the graph
     * @param graph the graph the diagram belongs to
     * @return the resolver
     */
    static EdgeResolver forGraph(String graphUri, Graph graph) {
        return forGraph(new CIMModelFacade(graphUri, ModelFactory.createModelForGraph(graph)));
    }

    /**
     * Resolves edges of the merged diagrams of a dataset, reading every graph of the dataset, see
     * {@link #forMergedProfiles(List)}.
     *
     * @param databasePort the database holding the dataset
     * @param datasetName the name of the dataset
     * @return the resolver
     */
    static EdgeResolver forDataset(DatabasePort databasePort, String datasetName) {
        return forMergedProfiles(
                CIMProfileModels.loadAll(databasePort, Map.of(), datasetName, null));
    }

    /**
     * Resolves edges of a merged diagram, i.e. the cross-profile diagram and the custom diagrams of
     * a dataset. Their classes are identified by the merged UUID of their IRI, and a merged class
     * has the edges of every graph that defines it.
     *
     * @param profiles every graph of the dataset
     * @return the resolver
     */
    static EdgeResolver forMergedProfiles(List<CIMProfileModel> profiles) {
        return mergedUUIDs -> {
            var edges = new LinkedHashMap<EdgeKey, String>();
            for (var profile : profiles) {
                for (var cimClass : profile.model().getCIMClasses()) {
                    var mergedUUID = mergedUuidOf(cimClass);
                    if (mergedUUIDs.contains(mergedUUID)) {
                        addEdgesOf(
                                cimClass,
                                mergedUUID,
                                mergedUUIDs,
                                EdgeResolver::mergedUuidOf,
                                edges);
                    }
                }
            }
            return edges;
        };
    }

    private static void addEdgesOf(
            ICIMClass cimClass,
            UUID classUUID,
            Set<UUID> classUUIDs,
            Function<ICIMResource, UUID> uuidOf,
            Map<EdgeKey, String> edges) {
        for (var superClass : cimClass.getSuperClasses()) {
            var superClassUUID = uuidOf.apply(superClass);
            if (superClassUUID != null && classUUIDs.contains(superClassUUID)) {
                edges.putIfAbsent(
                        EdgeKey.inheritance(classUUID, superClassUUID),
                        edgeName(cimClass, superClass));
            }
        }
        for (var association : cimClass.getAssociations()) {
            if (!association.isRenderable()) {
                continue;
            }
            var range = association.getRange();
            var rangeUUID = uuidOf.apply(range);
            var associationUUID = uuidOf.apply(association);
            var inverseUUID = uuidOf.apply(association.getInverseAssociation());
            if (rangeUUID == null
                    || !classUUIDs.contains(rangeUUID)
                    || associationUUID == null
                    || inverseUUID == null) {
                continue;
            }
            edges.putIfAbsent(
                    EdgeKey.association(associationUUID, inverseUUID), edgeName(cimClass, range));
        }
    }

    private static UUID mergedUuidOf(ICIMResource resource) {
        return CrossProfileUtils.mergedUuid(resource.getUri().toString());
    }

    /**
     * The name of an edge diagram object: the labels of the two classes it connects. The name is
     * only kept for readability, nothing resolves an edge by it.
     */
    private static String edgeName(ICIMResource from, ICIMResource to) {
        return labelOf(from) + " " + labelOf(to);
    }

    private static String labelOf(ICIMResource resource) {
        var label = resource.getLabelOrNull();
        return label != null ? label.getValue() : resource.getUri().getSuffix();
    }
}
