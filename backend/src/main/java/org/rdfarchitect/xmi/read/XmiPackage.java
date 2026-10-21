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

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** A {@code uml:Package} of an export. */
@Data
public class XmiPackage {

    private final String id;

    /** The package this one sits in, {@code null} for the outermost one. */
    private final String parentId;

    private final String name;

    private String documentation;

    /**
     * The tagged values the vendor extension puts on the package, by name. This is where a CGMES
     * 3.0 export states its namespaces and prefixes, under {@code nsuri} and {@code nsprefix}, and
     * the only place it does — the model section carries none of it.
     */
    private final Map<String, String> tags = new LinkedHashMap<>();
}
