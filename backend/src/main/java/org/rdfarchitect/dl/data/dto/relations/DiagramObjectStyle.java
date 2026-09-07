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

package org.rdfarchitect.dl.data.dto.relations;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The kind of thing a {@code cim:DiagramObject} stands for. Every diagram object carries one, which
 * is what lets the layout hold objects of different kinds side by side and query them apart.
 *
 * <p>The mRID is derived from the style name so that a style resolves to the same resource in every
 * model without having to be looked up before it can be referenced.
 */
public enum DiagramObjectStyle {
    CLASS("class"),
    MULTIPLICITY("multiplicity"),
    ASSOCIATION_LABEL("associationLabel");

    private final String styleName;
    private final MRID mRID;

    DiagramObjectStyle(String styleName) {
        this.styleName = styleName;
        this.mRID = mridOf(styleName);
    }

    public String getStyleName() {
        return styleName;
    }

    public MRID getMRID() {
        return mRID;
    }

    /**
     * Resolves a style by its name, for styles that reach the backend as free text.
     *
     * @param styleName the name of the style
     * @return the style, or null if no style carries that name
     */
    public static DiagramObjectStyle byName(String styleName) {
        for (var style : values()) {
            if (style.styleName.equals(styleName)) {
                return style;
            }
        }
        return null;
    }

    /**
     * Derives the deterministic mRID a style with the given name is stored under.
     *
     * @param styleName the name of the style
     * @return the mRID of the style
     */
    public static MRID mridOf(String styleName) {
        return new MRID(UUID.nameUUIDFromBytes(styleName.getBytes(StandardCharsets.UTF_8)));
    }
}
