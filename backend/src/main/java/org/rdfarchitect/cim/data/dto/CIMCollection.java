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

package org.rdfarchitect.cim.data.dto;

import lombok.Data;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

@Data
public class CIMCollection {

    public CIMCollection() {
        packages = new TreeSet<>(Comparator.comparing(pack -> pack.getUri().toString()));
        classes = new TreeSet<>(Comparator.comparing(cimClass -> cimClass.getUri().toString()));
        attributes = new TreeSet<>(Comparator.comparing(cimAttribute -> cimAttribute.getUri().toString()));
        associations = new TreeSet<>(Comparator.comparing(cimAssociation -> cimAssociation.getUri().toString()));
        enums = new TreeSet<>(Comparator.comparing(cimEnum -> cimEnum.getUri().toString()));
        enumEntries = new TreeSet<>(Comparator.comparing(enumEntry -> enumEntry.getUri().toString()));
    }

    private final SortedSet<CIMPackage> packages;

    private final SortedSet<CIMClass> classes;

    private final SortedSet<CIMAttribute> attributes;

    private final SortedSet<CIMAssociation> associations;

    private final SortedSet<CIMClass> enums;

    private final SortedSet<CIMEnumEntry> enumEntries;
}
