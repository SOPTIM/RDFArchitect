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

/**
 * One end of an association, as the vendor extension's {@code connector} states it.
 *
 * <p>An end does not describe the half of the pair its own class owns, but the half the class at
 * the <em>other</em> end owns. The end naming role {@code Terminals} on {@code Terminal}, opposite
 * {@code ConductingEquipment}, is what makes {@code ConductingEquipment.Terminals} — with this
 * end's multiplicity and this end's navigability. Reading the cross-over backwards points every
 * association the wrong way.
 */
@Data
public class XmiAssociationEnd {

    private final String connectorId;

    /** The class at this end, which becomes the half's range. */
    private final String classId;

    private final String className;

    /** The role this end names, which becomes the half's label and the last part of its URI. */
    private final String roleName;

    /** The class at the other end, which becomes the half's domain. */
    private final String oppositeClassId;

    private final String oppositeClassName;

    /** The role the other end names, from which the inverse role name is composed. */
    private final String oppositeRoleName;

    /**
     * This end's multiplicity, verbatim but for the unbounded upper bound. It is not expanded to
     * two bounds, because a release keeps {@code M:1} and {@code M:1..1} apart.
     */
    private final String multiplicity;

    /**
     * Whether this end is navigable, read from the {@code Navigable=} entry of its style. An export
     * states navigability twice and the two disagree; only this one reproduces a release.
     */
    private final boolean navigable;

    /** {@code shared} or {@code composite} makes the opposite half an aggregate. */
    private final String aggregation;

    private final String documentation;

    /**
     * The stereotypes applied to the connector this end belongs to. They are stated once for the
     * whole association, so both halves of the pair carry the same set.
     */
    private final Set<String> stereotypes = new LinkedHashSet<>();
}
