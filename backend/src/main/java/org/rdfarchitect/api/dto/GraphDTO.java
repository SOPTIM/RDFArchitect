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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;

/**
 * A graph of a dataset, together with what the CIM profile inside it says about itself.
 *
 * <p>Every field but the URI is absent for a graph that is not a CIM profile, and the CIM version
 * decides which of them a profile fills in: CGMES 2.4.15 has no version info, and a graph that
 * predates the profile metadata may have neither label nor description.
 */
@Data
@Builder
@AllArgsConstructor
public class GraphDTO {
    private URI uri;
    private String keyword;
    private String label;
    private String description;
    private List<String> versionIris;
    private String versionInfo;
}
