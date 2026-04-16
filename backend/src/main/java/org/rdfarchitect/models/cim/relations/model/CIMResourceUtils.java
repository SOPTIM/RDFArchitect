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

package org.rdfarchitect.models.cim.relations.model;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.UUID;

@UtilityClass
public class CIMResourceUtils {

    /**
     * Checks whether a resource is external/referenced only. In our model this would mean it only has a {@link RDFA::uuid} property, but no other properties.
     * @param resource The resource to check for.
     * @return True if the resource is an external resource, false otherwise.
     */
    public boolean isExternalResource(Resource resource) {
        return !resource.listProperties()
                    .filterDrop(stmt -> stmt.getPredicate().equals(RDFA.uuid))
                    .hasNext();
    }

    /**
     * Finds the uuid of a resource.
     * @param resource The resource to finde the uuid for.
     * @return The uuid of the resource.
     */
    public UUID findUuidForResource(Resource resource) {
        if (!resource.hasProperty(RDFA.uuid)) {
            throw new IllegalStateException("Resource " + resource + " does not have a UUID.");
        }
        return UUID.fromString(resource.getProperty(RDFA.uuid).getString());
    }
}
