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

import lombok.experimental.UtilityClass;

/** Reads what an export states in the {@code ;}-separated style string of a connector end. */
@UtilityClass
class XmiStyles {

    private final String NAVIGABLE_ENTRY = "Navigable=";

    private final String NAVIGABLE = "Navigable";

    /**
     * Whether an end is navigable.
     *
     * <p>An export states this twice and the two disagree. Only the style reproduces a release; the
     * obvious-looking {@code modifiers/@isNavigable} does not. {@code Navigable=Unspecified} is
     * written out and means not navigable, so an absent entry is read the same way.
     */
    boolean isNavigable(String style) {
        if (style == null) {
            return false;
        }
        for (var entry : style.split(";")) {
            var trimmed = entry.strip();
            if (trimmed.startsWith(NAVIGABLE_ENTRY)) {
                return NAVIGABLE.equals(trimmed.substring(NAVIGABLE_ENTRY.length()).strip());
            }
        }
        return false;
    }
}
