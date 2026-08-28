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

import java.util.List;

/**
 * The part of a class that an extension into another schema copies. Two schemas with an equal stub
 * produce the same class, no matter which of them is used.
 */
public record ClassStubDTO(
        String label,
        String comment,
        String superClassUri,
        String packageUri,
        String packageLabel,
        List<String> stereotypes) {}
