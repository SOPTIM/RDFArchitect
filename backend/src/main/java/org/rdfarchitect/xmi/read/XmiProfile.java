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

package org.rdfarchitect.xmi.read;

import java.util.List;

/**
 * A package of an Enterprise Architect export that holds a profile.
 *
 * @param id the package's {@code xmi:id}, which identifies it for the rest of the import
 * @param name the package's name, as the export spells it
 * @param namespaceCandidates every absolute IRI the metadata class declares as a profile URI, in
 *     declaration order. A released profile commonly offers several, of which one becomes the
 *     namespace and the rest become header fields
 */
public record XmiProfile(String id, String name, List<String> namespaceCandidates) {}
