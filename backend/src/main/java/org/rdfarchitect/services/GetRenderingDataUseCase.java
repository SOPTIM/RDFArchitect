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

package org.rdfarchitect.services;

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rendering.GraphFilter;

import java.util.UUID;

/**
 * Fetches and renders a graph as a UML diagram within a single READ transaction, ensuring a
 * consistent snapshot of both the RDF graph and the diagram layout.
 */
public interface GetRenderingDataUseCase {

    RenderingDataDTO getRenderingData(
            GraphIdentifier graphIdentifier, GraphFilter filter, UUID packageUUID);
}
