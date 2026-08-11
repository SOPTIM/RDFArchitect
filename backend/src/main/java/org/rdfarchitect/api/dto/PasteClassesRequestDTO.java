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

package org.rdfarchitect.api.dto;

import lombok.Data;

import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;
import java.util.UUID;

@Data
public class PasteClassesRequestDTO {

    UUID targetPackageUUID;
    boolean copyAsAbstract;
    boolean copyAttributes;
    boolean copyAssociations;
    boolean copyInheritance;

    /**
     * The referenced classes to copy along, out of the ones {@code /paste/preview} reports as
     * missing. A listed data type also brings the data types its own attributes need, so classes
     * the target graph does not contain yet may be copied without being listed here.
     */
    List<URI> referencesToCopy;

    List<PasteSourceClassDTO> sources;
}
