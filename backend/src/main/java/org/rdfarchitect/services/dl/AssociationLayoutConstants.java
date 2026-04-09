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

package org.rdfarchitect.services.dl;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AssociationLayoutConstants {

    public static final String LABEL_DECORATION_TYPE = "label";
    public static final String MULTIPLICITY_DECORATION_TYPE = "multiplicity";

    private static final String LABEL_DIAGRAM_OBJECT_NAME = "__association_label__";
    private static final String MULTIPLICITY_DIAGRAM_OBJECT_NAME = "__association_multiplicity__";

    public String getDiagramObjectName(String decorationType) {
        return switch (decorationType) {
            case LABEL_DECORATION_TYPE -> LABEL_DIAGRAM_OBJECT_NAME;
            case MULTIPLICITY_DECORATION_TYPE -> MULTIPLICITY_DIAGRAM_OBJECT_NAME;
            default -> throw new IllegalArgumentException("Unexpected association decoration type: " + decorationType);
        };
    }

    public String getDecorationTypeForDiagramObjectName(String diagramObjectName) {
        return switch (diagramObjectName) {
            case LABEL_DIAGRAM_OBJECT_NAME -> LABEL_DECORATION_TYPE;
            case MULTIPLICITY_DIAGRAM_OBJECT_NAME -> MULTIPLICITY_DECORATION_TYPE;
            default -> null;
        };
    }
}
