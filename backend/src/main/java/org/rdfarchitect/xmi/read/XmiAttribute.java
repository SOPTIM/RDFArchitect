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

import java.util.LinkedHashSet;
import java.util.Set;

/** An {@code ownedAttribute} of a class, as far as an import reads it. */
@Data
public class XmiAttribute {

    private final String id;

    private final String name;

    /** The classifier the attribute is typed with, {@code null} when it declares no type. */
    private String typeId;

    /** Lower bound as the export spells it, {@code null} when it declares none. */
    private String lowerBound;

    /** Upper bound as the export spells it, with its two spellings of unbounded normalised. */
    private String upperBound;

    private String defaultValue;

    /**
     * Whether the default value cannot be changed. The export states this in its vendor extension
     * while the value itself is in the model part, so the two have to be read together.
     */
    private boolean fixed;

    private String documentation;

    private final Set<String> stereotypes = new LinkedHashSet<>();
}
