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

package org.rdfarchitect.services.validation.workspace.index;

import org.rdfarchitect.services.rendering.CIMProfileModel;

import java.util.UUID;

/**
 * A resource that could not be read while indexing a schema.
 *
 * @param profile the schema it was found in
 * @param uuid the resource, or null when the whole schema could not be read
 * @param message what went wrong
 */
public record ReadFailure(CIMProfileModel profile, UUID uuid, String message) {}
