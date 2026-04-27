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

package org.rdfarchitect.api.dto.delete.relations;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import org.rdfarchitect.api.dto.delete.DeleteAction;
import org.rdfarchitect.api.dto.delete.ResourceIdentifier;
import org.rdfarchitect.models.cim.relations.model.CIMResourceTypeIdentifyingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AffectedResource {

    private ResourceIdentifier resourceIdentifier;

    private CIMResourceTypeIdentifyingUtils.CimResourceType type;

    private AffectedResourceReason reason;

    private List<DeleteAction> actions = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<AffectedResource> children = new ArrayList<>();

    private Map<String, String> context = new HashMap<>();

    public AffectedResource(
            ResourceIdentifier resourceIdentifier,
            CIMResourceTypeIdentifyingUtils.CimResourceType type,
            AffectedResourceReason reason) {
        this.resourceIdentifier = resourceIdentifier;
        this.type = type;
        this.reason = reason;
    }

    public List<AffectedResource> getChildren() {
        return new ArrayList<>(children);
    }

    public AffectedResource setChildren(List<AffectedResource> children) {
        this.children = (children == null) ? new ArrayList<>() : new ArrayList<>(children);
        return this;
    }

    public enum AffectedResourceReason {
        CONTAINED_IN_PACKAGE,
        USES_DELETED_CLASS_AS_DATATYPE,
        REFENCES_DELETED_CLASS_VIA_ASSOCIATION,
        CHILD_OF,
        USES_DELETED_CLASS_AS_DEFAULT_VALUE,
        USES_DELETED_CLASS_AS_FIXED_VALUE,
        DELETION_REQUESTED_BY_USER,
    }
}
