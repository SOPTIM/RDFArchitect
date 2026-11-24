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
 * A class as it is drawn on a diagram.
 *
 * <p>Only a {@code UMLClassifierShape} becomes one of these. The shape types next to it carry no
 * classifier: {@code UMLShape} is used for notes, boundaries and text, and every {@code
 * UMLCompartmentableShape} points at a package, which has no counterpart because a package here is
 * a diagram rather than a node on one.
 */
@Data
public class XmiShape {

    /** The class drawn, by {@code xmi:id}. */
    private final String classId;

    private final int x;

    private final int y;

    /**
     * The z-order, which UMLDI does not state anywhere. It is joined in from the vendor extension's
     * own diagram list, and stays {@code 0} for a shape that list does not mention.
     */
    private int zPosition;
}
