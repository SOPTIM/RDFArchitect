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

    public AffectedResource(ResourceIdentifier resourceIdentifier,
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
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
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

    /*
    Eine Assoziation kann nur von der inverse gelöscht referenziert werden, da aber immer alles zusammen gelöscht wird gibt es ein trickle effect über den man entscheiden muss
     */

    /*
    Ein Attribut zu löschen hat keine Auswirkung auf irgend etwas anderes
     */

    /*
    other classes
    Another class is extending this one
    - Class A (Keep refence to deleted Class) (delete reference) (Delete Class? (würde ich erstmal als overkill ansehen))

    Attributes (sowohl enum als auch normal)
    This class is used as a Datatype in the following attributes:
    How would you like to proceed
    - Attr1 von Klasse A (unreferenced Datatype) (Delete Attribute)
    ...

    This class is Referenced Via an association as a Target:
    - Association1 von Klasse A (unreferenced Target) (Delete Association)
    ...
     */

    /*
    Ein enum entry kann von theoretisch als default wert referenziert werden.
    Problem ist, dass man dann beim löschen eines enum entries den put request analysieren muss, welche operation ausgeführt wird.
    Aber wenn man das macht:
    - Deleting a used enum entry in a default value. (delete Attribute) (delete default value)
     */

    /*
    Do you want to delete the contents of this package?
    - class A (Delete Class (extend into classDropdown)) (keep reference) (remove reference)
    ...
     */
