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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@code uml:Class} or {@code uml:Enumeration} of an export.
 *
 * <p>Both arrive as classes: an export writes {@code uml:Boundary} and {@code uml:Text} as {@code
 * uml:Class} too, which only the vendor extension tells apart, so what is kept here is what
 * survived that check.
 */
@Data
public class XmiClass {

    private final String id;

    private final String name;

    /** The package the class is declared in, {@code null} when it sits outside any. */
    private final String packageId;

    /** Whether the export marks the class abstract. RDFArchitect states the opposite. */
    private boolean isAbstract;

    /** Whether the class is a {@code uml:Enumeration} rather than a {@code uml:Class}. */
    private boolean enumeration;

    private String documentation;

    /**
     * Every {@code generalization} the class declares. A class may declare several while
     * RDFArchitect holds one superclass, so which one wins is the mapping's problem, not this
     * reader's.
     */
    private final List<String> superClassIds = new ArrayList<>();

    private final List<XmiAttribute> attributes = new ArrayList<>();

    /** The {@code ownedLiteral} entries of an enumeration, in declaration order. */
    private final List<XmiEnumLiteral> literals = new ArrayList<>();

    /**
     * The complete stereotype set, read from the stereotype applications at the root of the file.
     * The {@code stereotype} attributes of the vendor extension hold one value each and lose the
     * co-occurring ones a release does emit.
     */
    private final Set<String> stereotypes = new LinkedHashSet<>();
}
