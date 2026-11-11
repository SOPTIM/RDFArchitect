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
import java.util.List;

/**
 * One class diagram of an export, drawn from both parts of the file.
 *
 * <p>UMLDI holds the diagram and everything on it; the vendor extension's own diagram list holds
 * the name and the z-order, neither of which UMLDI states anywhere. The two are joined over the
 * diagram's {@code xmi:id}, which both parts spell the same way.
 *
 * <p>What arrives is thinner than what Enterprise Architect shows: about three quarters of the
 * entries resolve to a classifier, the rest being notes, boundaries and package shapes that have no
 * counterpart, while the layout was drawn around them.
 */
@Data
public class XmiDiagram {

    private final String id;

    /** The package the diagram is declared in, which is how it is attributed to a profile. */
    private final String owningPackageId;

    /** Stated by the vendor extension only; {@code null} when it does not list the diagram. */
    private String name;

    private final List<XmiShape> shapes = new ArrayList<>();

    private final List<XmiLabel> labels = new ArrayList<>();
}
