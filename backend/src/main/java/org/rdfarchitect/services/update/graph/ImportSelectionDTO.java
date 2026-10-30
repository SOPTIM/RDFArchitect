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

package org.rdfarchitect.services.update.graph;

import java.util.List;

/**
 * What to import out of a read export.
 *
 * @param packages the packages to build as schemas, in the order they are to be built
 * @param diagramIds the class diagrams to keep, by {@code xmi:id}. A diagram whose classes did not
 *     all make it is left out whether or not it is named here: the caller works out what is
 *     available from the read, and the import is the one that knows
 */
public record ImportSelectionDTO(List<SelectedPackageDTO> packages, List<String> diagramIds) {

    /** The packages as given, or none. */
    public List<SelectedPackageDTO> packages() {
        return packages == null ? List.of() : packages;
    }

    /** The diagrams as given, or none. */
    public List<String> diagramIds() {
        return diagramIds == null ? List.of() : diagramIds;
    }
}
