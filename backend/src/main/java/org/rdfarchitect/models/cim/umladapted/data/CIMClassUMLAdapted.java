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

package org.rdfarchitect.models.cim.umladapted.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@Data
public class CIMClassUMLAdapted extends CIMClass {

    public CIMClassUMLAdapted(CIMClass cimClass) {
        super();
        this.setUri(cimClass.getUri());
        this.setUuid(cimClass.getUuid());
        this.setLabel(cimClass.getLabel());
        this.setComment(cimClass.getComment());
        this.setSuperClass(cimClass.getSuperClass());
        this.setBelongsToCategory(cimClass.getBelongsToCategory());
        this.setStereotypes(cimClass.getStereotypes());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CIMAttribute> attributes = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CIMEnumEntry> enumEntries = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CIMAssociationPair> associationPairs = new ArrayList<>();

    public void nullEmptyLists() {
        super.nullStereotypes();
        if (attributes.isEmpty()) {
            attributes = null;
        }
        if (associationPairs.isEmpty()) {
            associationPairs = null;
        }
    }
}
