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

/**
 * The label of one association end as it is placed on a diagram.
 *
 * <p>A label does not name the end it belongs to the way a shape names its class. It sits inside
 * the edge that draws the association, which is what says <em>which</em> association, and its own
 * {@code modelElement} is a synthetic id whose {@code EAID_src} or {@code EAID_dst} prefix is what
 * says <em>which end</em>. That prefix is the only discriminator, and every label of every released
 * export carries one of the two.
 */
@Data
public class XmiLabel {

    /** The association drawn, by the {@code xmi:id} of its connector. */
    private final String connectorId;

    /** Whether the label belongs to the connector's source end rather than its target end. */
    private final boolean atSourceEnd;

    private final Kind kind;

    private final int x;

    private final int y;

    /** What a label states, which decides the kind of position it becomes. */
    public enum Kind {
        MULTIPLICITY,
        ASSOCIATION_END
    }
}
