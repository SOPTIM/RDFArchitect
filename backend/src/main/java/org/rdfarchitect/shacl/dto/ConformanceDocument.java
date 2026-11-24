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

package org.rdfarchitect.shacl.dto;

import java.util.UUID;

/**
 * One of the documents a conformance report read.
 *
 * <p>Carries the id as well as the name so a finding can be opened where it is stated. {@link
 * ConformanceFinding#getStatedIn()} names documents, because that is what reads well in a sentence;
 * the id is what the workbench link needs, and names are unique within a graph so the two line up.
 *
 * @param id the document's id
 * @param name its display name, as {@code statedIn} spells it
 */
public record ConformanceDocument(UUID id, String name) {}
