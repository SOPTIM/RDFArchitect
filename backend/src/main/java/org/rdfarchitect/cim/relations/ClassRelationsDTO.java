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

package org.rdfarchitect.cim.relations;

import lombok.Data;
import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.cim.data.dto.CIMClass;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class ClassRelationsDTO {

    private UUID uuid;

    private Map<String, Collection<ClassDTO>> classesReferencingThisClass;

    public void setClassesReferencingThisClassFromCIM(Map<String, Collection<CIMClass>> cimClassesMap) {
        if (cimClassesMap == null) {
            this.classesReferencingThisClass = null;
            return;
        }

        this.classesReferencingThisClass = cimClassesMap.entrySet()
                                                        .stream()
                                                        .collect(Collectors.toMap(
                                                                  Map.Entry::getKey,
                                                                  entry -> entry.getValue()
                                                                                .stream()
                                                                                .map(ClassMapper.INSTANCE::toDTO)
                                                                                .collect(Collectors.toList())
                                                                                 )
                                                                );
    }
}
