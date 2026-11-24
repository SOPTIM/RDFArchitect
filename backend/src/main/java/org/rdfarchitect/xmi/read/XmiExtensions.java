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

package org.rdfarchitect.xmi.read;

import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * Which stereotypes of an export mark a resource as belonging to the extensions rather than to the
 * model itself.
 *
 * <p>Here rather than beside the mapping, although that is what acts on it: whether two classes
 * would end up under one URI is a question the read has to answer too, when it counts what a
 * package costs to import, and the reading side cannot depend on the mapping one.
 */
@UtilityClass
public class XmiExtensions {

    private final Set<String> EXTENSION_STEREOTYPES = Set.of("Entsoe", "European");

    /**
     * Whether these stereotypes put a resource in the extension namespace rather than the model.
     */
    public boolean isExtension(Set<String> applied) {
        return applied.stream().anyMatch(EXTENSION_STEREOTYPES::contains);
    }
}
