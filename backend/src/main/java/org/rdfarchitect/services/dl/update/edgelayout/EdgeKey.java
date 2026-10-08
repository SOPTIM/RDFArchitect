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

package org.rdfarchitect.services.dl.update.edgelayout;

import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;

import java.util.UUID;

/**
 * Identifies an edge within a diagram by the two identified objects it connects. The pair is what
 * an edge diagram object stores in {@code DiagramObject.IdentifiedObject} and {@code
 * rdfa:DiagramObject.otherClass}:
 *
 * <ul>
 *   <li>an inheritance edge is identified by its sub class and its super class, so a class with
 *       several super classes (possible in merged diagrams) gets one edge per super class
 *   <li>an association edge is identified by its two association ends. Either end may be stored as
 *       the identified object, so the ends are kept in a fixed order to compare keys regardless of
 *       which end a diagram object references first
 * </ul>
 *
 * In merged diagrams the UUIDs are the merged UUIDs derived from the IRIs instead of the UUIDs a
 * resource carries in its graph.
 *
 * @param style {@link DiagramObjectStyle#INHERITANCE} or {@link DiagramObjectStyle#ASSOCIATION}
 * @param identifiedObject the sub class, or the association end that sorts first
 * @param otherClass the super class, or the association end that sorts last
 */
public record EdgeKey(DiagramObjectStyle style, UUID identifiedObject, UUID otherClass) {

    public static EdgeKey inheritance(UUID subClass, UUID superClass) {
        return new EdgeKey(DiagramObjectStyle.INHERITANCE, subClass, superClass);
    }

    public static EdgeKey association(UUID associationEnd, UUID inverseAssociationEnd) {
        if (associationEnd.compareTo(inverseAssociationEnd) <= 0) {
            return new EdgeKey(
                    DiagramObjectStyle.ASSOCIATION, associationEnd, inverseAssociationEnd);
        }
        return new EdgeKey(DiagramObjectStyle.ASSOCIATION, inverseAssociationEnd, associationEnd);
    }

    /**
     * The key of an edge given by its type and the two identified objects it connects, in either
     * order for associations.
     *
     * @param style {@link DiagramObjectStyle#INHERITANCE} or {@link DiagramObjectStyle#ASSOCIATION}
     * @param sourceObject the sub class, or one of the association ends
     * @param targetObject the super class, or the other association end
     * @return the key, or null if the style is no edge style
     */
    public static EdgeKey of(DiagramObjectStyle style, UUID sourceObject, UUID targetObject) {
        if (style == DiagramObjectStyle.INHERITANCE) {
            return inheritance(sourceObject, targetObject);
        }
        if (style == DiagramObjectStyle.ASSOCIATION) {
            return association(sourceObject, targetObject);
        }
        return null;
    }

    /**
     * The key of an edge diagram object as stored in the diagram layout.
     *
     * @param diagramObject an edge diagram object
     * @return the key, or null if the diagram object is no edge or lacks one of the two identified
     *     objects
     */
    public static EdgeKey of(DiagramObject diagramObject) {
        var identifiedObject = diagramObject.getBelongsToIdentifiedObject();
        var otherClass = diagramObject.getOtherClass();
        if (identifiedObject == null || otherClass == null) {
            return null;
        }
        return of(
                diagramObject.getBelongsToDiagramObjectStyle(),
                identifiedObject.getUuid(),
                otherClass.getUuid());
    }
}
