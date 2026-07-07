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

package org.rdfarchitect.services.update.classes;

import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

/**
 * Identifies a single class to be copied, including the graph it originates from.
 *
 * @param graphIdentifier The graph URI and dataset name of the graph the class is copied from.
 * @param classUUID The UUID of the class that will be copied.
 */
public record CopyClassSource(GraphIdentifier graphIdentifier, UUID classUUID) {}
