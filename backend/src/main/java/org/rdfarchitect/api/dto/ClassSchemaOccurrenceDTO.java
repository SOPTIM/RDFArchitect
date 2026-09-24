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

import java.util.UUID;

/**
 * What one schema of a workspace has to say about a class.
 *
 * <p>The schema is described by the same fields the schema pickers name a graph with — {@code
 * label} first, {@code keyword} when it has no name — so that a class is offered under the name the
 * navigation tree shows it under.
 */
public record ClassSchemaOccurrenceDTO(
        String graphUri,
        String keyword,
        String label,
        boolean present,
        UUID classUUID,
        ClassStubDTO stub) {}
