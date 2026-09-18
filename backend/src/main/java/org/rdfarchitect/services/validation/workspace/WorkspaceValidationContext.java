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

package org.rdfarchitect.services.validation.workspace;

import lombok.Getter;

import org.rdfarchitect.models.cim.data.dto.facade.header.ICIMProfileHeader;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.index.WorkspaceIndex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WorkspaceValidationContext {

    @Getter private final List<CIMProfileModel> profiles;

    private final Map<String, ICIMProfileHeader> headers = new HashMap<>();

    private WorkspaceIndex index;

    /**
     * @param profiles the schemas of the workspace, in the order they are stored
     */
    public WorkspaceValidationContext(List<CIMProfileModel> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    /**
     * The index over all schemas, built on first use.
     *
     * @return the index, the same one for every rule of this run
     */
    public WorkspaceIndex getIndex() {
        if (index == null) {
            index = WorkspaceIndex.of(profiles);
        }
        return index;
    }

    /**
     * The header of one schema, read once per schema.
     *
     * @return the header, never null, see {@link ICIMProfileHeader#isPresent()}
     */
    public ICIMProfileHeader getHeader(CIMProfileModel profile) {
        return headers.computeIfAbsent(profile.graphUri(), _ -> profile.model().getHeader());
    }

    /**
     * The short name a schema goes by, for instance {@code EQ}.
     *
     * @return the keyword from the header, or null when the schema has none
     */
    public String getKeyword(CIMProfileModel profile) {
        if (profile.keyword() != null) {
            return profile.keyword();
        }
        return getHeader(profile).getKeyword();
    }

    Optional<WorkspaceIndex> getBuiltIndex() {
        return Optional.ofNullable(index);
    }
}
