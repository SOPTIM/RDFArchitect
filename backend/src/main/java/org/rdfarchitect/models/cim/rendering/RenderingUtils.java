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

package org.rdfarchitect.models.cim.rendering;

import lombok.experimental.UtilityClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class RenderingUtils {

    /**
     * Assigns a {@link UUID} to each uri in the cimCollection.
     * The UUIDs are used as ids for each object in the mermaid String since uris can contain chars that are not allowed in the mermaid syntax
     */
    public static Map<String, UUID> createUUIDUriPairs(CIMCollection cimCollection) {
        Map<String, UUID> uriToUUIDMap = new HashMap<>();

        cimCollection.getPackages().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getClasses().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getAttributes().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getAssociations().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getEnums().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getEnumEntries().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        return uriToUUIDMap;
    }

    public static boolean hasRenderableClasses(CIMCollection cimCollection) {
        return !cimCollection.getClasses().isEmpty() ||
               !cimCollection.getEnums().isEmpty();
    }
}
