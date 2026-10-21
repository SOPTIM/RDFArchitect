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

import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.stream.XMLStreamReader;

/**
 * Reads the class diagrams, which are the one thing written in two parts that are not next to each
 * other: UMLDI sits in the model section, the vendor extension's own diagram list at the end of the
 * extension. Both are needed, and they are joined over the diagram's {@code xmi:id}.
 *
 * <pre>{@code
 * <umldi:Diagram xmi:type="umldi:UMLClassDiagram" xmi:id="D1" modelElement="EAPK_1">
 *   <ownedElement xmi:type="umldi:UMLClassifierShape" modelElement="EAID_1">   a class
 *     <ownedElement xmi:type="umldi:UMLNameLabel" text="Terminal"/>            not a shape
 *     <bounds x="711" y="1451"/>                                              where it sits
 *   </ownedElement>
 *   <ownedElement xmi:type="umldi:UMLEdge" modelElement="EAID_CD81BB05_…">     an association
 *     <ownedElement xmi:type="umldi:UMLMultiplicityLabel"
 *                   modelElement="EAID_src81BB05_…">                          its source end
 *       <bounds x="1075" y="436"/>
 *     </ownedElement>
 *     <waypoint x="1072" y="453"/>                                            not imported
 *   </ownedElement>
 * </umldi:Diagram>
 *
 * <diagrams>
 *   <diagram xmi:id="D1">
 *     <properties name="Topology" type="Logical"/>                            the name
 *     <elements>
 *       <element subject="EAID_1" seqno="3"/>                                 the z-order
 *     </elements>
 *   </diagram>
 * </diagrams>
 * }</pre>
 *
 * <p>Two things are worth knowing about the shape of this data. {@code <ownedElement>} nests — a
 * shape owns its name and keyword labels, an edge owns its end labels — so which element a {@code
 * <bounds>} belongs to is whatever is innermost, which is what the stack is for. And a label states
 * its end through the {@code EAID_src} / {@code EAID_dst} prefix of a synthetic {@code
 * modelElement}, not through an id anything else in the file uses.
 *
 * @see org.rdfarchitect.xmi.read
 */
@RequiredArgsConstructor
final class XmiDiagramReader {

    /** What a label's {@code modelElement} begins with when the label belongs to the source end. */
    private static final String SOURCE_END_PREFIX = "EAID_src";

    private final XmiReadContext context;

    private XmiDiagram currentDiagram;
    private final Deque<Drawn> drawn = new ArrayDeque<>();

    private String shapeClassId;
    private String edgeConnectorId;
    private PendingLabel pendingLabel;

    private XmiDiagram currentListedDiagram;

    // -------------------------------------------------------------------------
    // UMLDI, in the model section
    // -------------------------------------------------------------------------

    void startInModel(XMLStreamReader reader) {
        switch (reader.getLocalName()) {
            case XmiNames.UMLDI_DIAGRAM -> startDiagram(reader);
            case XmiNames.OWNED_ELEMENT -> startDrawn(reader);
            case XmiNames.BOUNDS -> placeInnermost(reader);
            default -> {
                // Waypoints, name labels and keyword labels carry nothing an import reads.
            }
        }
    }

    void endInModel(String element) {
        switch (element) {
            case XmiNames.UMLDI_DIAGRAM -> currentDiagram = null;
            case XmiNames.OWNED_ELEMENT -> endDrawn();
            default -> {
                // See startInModel.
            }
        }
    }

    /**
     * Object and package diagrams have no counterpart, so one leaves {@code currentDiagram} unset
     * and everything drawn on it falls away with it.
     */
    private void startDiagram(XMLStreamReader reader) {
        if (!XmiNames.UMLDI_CLASS_DIAGRAM.equals(XmiStreams.attribute(reader, XmiNames.TYPE))) {
            return;
        }
        var id = XmiStreams.attribute(reader, XmiNames.ID);
        if (id == null) {
            return;
        }
        currentDiagram = new XmiDiagram(id, XmiStreams.attribute(reader, XmiNames.MODEL_ELEMENT));
        context.getIndex().getDiagrams().put(id, currentDiagram);
    }

    private void startDrawn(XMLStreamReader reader) {
        if (currentDiagram == null) {
            return;
        }
        var type = XmiStreams.attribute(reader, XmiNames.TYPE);
        var modelElement = XmiStreams.attribute(reader, XmiNames.MODEL_ELEMENT);
        switch (type == null ? "" : type) {
            case XmiNames.UMLDI_CLASSIFIER_SHAPE -> {
                drawn.push(Drawn.SHAPE);
                shapeClassId = modelElement;
            }
            case XmiNames.UMLDI_EDGE -> {
                drawn.push(Drawn.EDGE);
                edgeConnectorId = modelElement;
            }
            case XmiNames.UMLDI_MULTIPLICITY_LABEL ->
                    startLabel(modelElement, XmiLabel.Kind.MULTIPLICITY);
            case XmiNames.UMLDI_ASSOCIATION_END_LABEL ->
                    startLabel(modelElement, XmiLabel.Kind.ASSOCIATION_END);
            default -> drawn.push(Drawn.OTHER);
        }
    }

    /** A label outside an edge names no association and is dropped rather than guessed at. */
    private void startLabel(String modelElement, XmiLabel.Kind kind) {
        if (edgeConnectorId == null || modelElement == null) {
            drawn.push(Drawn.OTHER);
            return;
        }
        drawn.push(Drawn.LABEL);
        pendingLabel =
                new PendingLabel(edgeConnectorId, modelElement.startsWith(SOURCE_END_PREFIX), kind);
    }

    /**
     * A {@code <bounds>} belongs to whichever element is innermost, so a name label inside a shape
     * does not place the shape and a waypoint does not place anything.
     */
    private void placeInnermost(XMLStreamReader reader) {
        if (currentDiagram == null || drawn.isEmpty()) {
            return;
        }
        var x = coordinate(reader, XmiNames.X);
        var y = coordinate(reader, XmiNames.Y);
        switch (drawn.peek()) {
            case SHAPE -> {
                if (shapeClassId != null) {
                    currentDiagram.getShapes().add(new XmiShape(shapeClassId, x, y));
                }
            }
            case LABEL -> {
                if (pendingLabel != null) {
                    currentDiagram
                            .getLabels()
                            .add(
                                    new XmiLabel(
                                            pendingLabel.connectorId(),
                                            pendingLabel.atSourceEnd(),
                                            pendingLabel.kind(),
                                            x,
                                            y));
                }
            }
            default -> {
                // An edge is routed rather than placed, and nothing else carries a position.
            }
        }
    }

    private void endDrawn() {
        if (currentDiagram == null || drawn.isEmpty()) {
            return;
        }
        switch (drawn.poll()) {
            case SHAPE -> shapeClassId = null;
            case EDGE -> edgeConnectorId = null;
            case LABEL -> pendingLabel = null;
            case OTHER -> {
                // Nothing was opened for it.
            }
        }
    }

    // -------------------------------------------------------------------------
    // The vendor extension's own diagram list
    // -------------------------------------------------------------------------

    void startInDiagrams(XMLStreamReader reader) {
        switch (reader.getLocalName()) {
            case XmiNames.DIAGRAM ->
                    currentListedDiagram =
                            context.getIndex()
                                    .getDiagrams()
                                    .get(XmiStreams.attribute(reader, XmiNames.ID));
            case XmiNames.PROPERTIES -> nameListedDiagram(reader);
            case XmiNames.ELEMENT -> applyZPosition(reader);
            default -> {
                // Styles, swimlanes, cross references and project data are not imported.
            }
        }
    }

    void endInDiagrams(String element) {
        if (XmiNames.DIAGRAM.equals(element)) {
            currentListedDiagram = null;
        }
    }

    private void nameListedDiagram(XMLStreamReader reader) {
        if (currentListedDiagram != null) {
            currentListedDiagram.setName(XmiStreams.attribute(reader, XmiNames.NAME));
        }
    }

    /**
     * The z-order exists in this list alone. An entry without a {@code seqno} draws an edge, which
     * is not ordered, and one naming something no shape was read for has nothing to apply to.
     */
    private void applyZPosition(XMLStreamReader reader) {
        if (currentListedDiagram == null) {
            return;
        }
        var subject = XmiStreams.attribute(reader, XmiNames.SUBJECT);
        var seqno = XmiStreams.attribute(reader, XmiNames.SEQNO);
        if (subject == null || seqno == null) {
            return;
        }
        for (var shape : currentListedDiagram.getShapes()) {
            if (subject.equals(shape.getClassId())) {
                shape.setZPosition(coordinateOf(seqno));
            }
        }
    }

    // -------------------------------------------------------------------------

    private static int coordinate(XMLStreamReader reader, String name) {
        return coordinateOf(XmiStreams.attribute(reader, name));
    }

    /** A coordinate an export writes in a form no number can be read from counts as none. */
    private static int coordinateOf(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /** What is open around a {@code <bounds>}; see {@link #placeInnermost}. */
    private enum Drawn {
        SHAPE,
        EDGE,
        LABEL,
        OTHER
    }

    /** What has been read of a label before its position arrives. */
    private record PendingLabel(String connectorId, boolean atSourceEnd, XmiLabel.Kind kind) {}
}
