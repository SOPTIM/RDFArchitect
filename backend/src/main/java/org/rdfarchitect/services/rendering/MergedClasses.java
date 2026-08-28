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

package org.rdfarchitect.services.rendering;

import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolution helpers for classes that are merged across the profiles of a dataset. A merged class
 * has no uuid of its own in any graph and is keyed by the uuid {@link
 * org.rdfarchitect.services.diagrams.CrossProfileUtils#mergedUuid(String)} derives from its IRI.
 */
public final class MergedClasses {

    private MergedClasses() {}

    /**
     * Resolves the classes a custom diagram holds to the IRIs of their merged nodes. Entries whose
     * graph or class no longer exists are dropped.
     */
    public static Set<String> renderedClassUris(
            List<CIMProfileModel> profiles, List<ClassInDiagram> classesInDiagram) {
        return new LinkedHashSet<>(
                classUriByUuid(classesInDiagram, modelsByGraphUri(profiles)).values());
    }

    /**
     * Resolves the classes a custom diagram holds to their IRIs, keyed by the uuid they carry in
     * their graph. Only the classes of the diagram are read, not those of the whole graph.
     */
    public static Map<UUID, String> classUriByUuid(
            List<ClassInDiagram> classesInDiagram, Map<String, ICIMModelFacade> modelsByGraphUri) {
        var classUriByUuid = new LinkedHashMap<UUID, String>();
        for (var classInDiagram : classesInDiagram) {
            if (classInDiagram.getGraphUri() == null || classInDiagram.getUuid() == null) {
                continue;
            }
            var model = modelsByGraphUri.get(classInDiagram.getGraphUri().toString());
            if (model == null) {
                continue;
            }
            var cimClass = model.getCIMClass(classInDiagram.getUuid());
            if (cimClass != null) {
                classUriByUuid.put(classInDiagram.getUuid(), cimClass.getUri().toString());
            }
        }
        return classUriByUuid;
    }

    private static Map<String, ICIMModelFacade> modelsByGraphUri(List<CIMProfileModel> profiles) {
        var modelsByGraphUri = new HashMap<String, ICIMModelFacade>();
        for (var profile : profiles) {
            modelsByGraphUri.putIfAbsent(profile.graphUri(), profile.model());
        }
        return modelsByGraphUri;
    }
}
