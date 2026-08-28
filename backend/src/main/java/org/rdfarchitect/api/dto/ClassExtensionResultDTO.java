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
 * Result of extending one class into another graph. {@code created} is false when the class was
 * already defined there, in which case the identifiers point to the class that was found.
 */
public record ClassExtensionResultDTO(
        UUID sourceClassUUID, UUID classUUID, UUID packageUUID, boolean created) {}
