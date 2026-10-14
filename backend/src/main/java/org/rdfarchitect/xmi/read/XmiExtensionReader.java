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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamReader;

/**
 * Reads the vendor extension: the documentation, the real element types, whether a default value
 * can be changed, and the connectors both halves of an association come from.
 *
 * <p>It creates nothing of its own. Everything it has to say belongs to something the model part
 * already read, which it finds by the id the extension repeats — except the association ends, which
 * exist in the connectors alone.
 *
 * <pre>{@code
 * <element xmi:idref="EAID_1" xmi:type="uml:Class">        startElement
 *   <properties documentation="A node." isAbstract="false"/>  applyProperties, CLASS
 *   <attribute xmi:idref="EAID_2">                         startAttribute
 *     <documentation value="Injected power."/>             setDocumentation, ATTRIBUTE
 *     <properties changeability="frozen"/>                 applyProperties, ATTRIBUTE
 *   </attribute>
 * </element>
 *
 * <connector xmi:idref="EAID_7">                           startConnector
 *   <source xmi:idref="EAID_1">                            endAt
 *     <model name="TopologicalNode"/>                      applyEndModel
 *     <role name="Terminal"/>                              applyEndRole
 *     <type multiplicity="0..*" aggregation="none"/>       applyEndType
 *     <style value="…;Navigable=Navigable;…"/>             applyEndStyle
 *   </source>
 *   <target xmi:idref="EAID_6">…</target>
 *   <properties ea_type="Association"/>                    applyProperties, CONNECTOR
 * </connector>                                             endConnector, both halves
 * }</pre>
 *
 * <p>{@code <properties>} and {@code <documentation>} sit in four different places and mean
 * something different in each, so what they are read as is decided by {@link #openNow()}.
 *
 * @see org.rdfarchitect.xmi.read
 */
@RequiredArgsConstructor
final class XmiExtensionReader {

    private final XmiReadContext context;

    /**
     * The {@code xmi:type} the extension gives an element, which the model part gets wrong: an
     * export writes {@code uml:Boundary} and {@code uml:Text} as {@code uml:Class}.
     */
    @Getter private final Map<String, String> typesById = new HashMap<>();

    private String currentElementId;
    private XmiClass currentClass;
    private XmiAttribute currentAttribute;
    private XmiEnumLiteral currentLiteral;

    private ConnectorEnd source;
    private ConnectorEnd target;
    private ConnectorEnd currentEnd;
    private String currentConnectorId;
    private String currentConnectorType;

    void start(XMLStreamReader reader) {
        switch (reader.getLocalName()) {
            case XmiNames.ELEMENT -> startElement(reader);
            case XmiNames.ATTRIBUTE -> startAttribute(reader);
            case XmiNames.DOCUMENTATION ->
                    setDocumentation(XmiStreams.attribute(reader, XmiNames.VALUE));
            case XmiNames.PROPERTIES -> applyProperties(reader);
            case XmiNames.CONNECTOR -> startConnector(reader);
            case XmiNames.SOURCE -> currentEnd = source = endAt(reader);
            case XmiNames.TARGET -> currentEnd = target = endAt(reader);
            case XmiNames.END_MODEL -> applyEndModel(reader);
            case XmiNames.ROLE -> applyEndRole(reader);
            case XmiNames.TYPE -> applyEndType(reader);
            case XmiNames.STYLE -> applyEndStyle(reader);
            default -> {
                // Styles, coordinates, cross references and project data are not imported.
            }
        }
    }

    void end(String element) {
        switch (element) {
            case XmiNames.ELEMENT -> {
                currentElementId = null;
                currentClass = null;
            }
            case XmiNames.ATTRIBUTE -> {
                currentAttribute = null;
                currentLiteral = null;
            }
            case XmiNames.SOURCE, XmiNames.TARGET -> currentEnd = null;
            case XmiNames.CONNECTOR -> endConnector();
            default -> {
                // See start.
            }
        }
    }

    private void startElement(XMLStreamReader reader) {
        currentElementId = XmiStreams.attribute(reader, XmiNames.IDREF);
        var type = XmiStreams.attribute(reader, XmiNames.TYPE);
        if (currentElementId != null && type != null) {
            typesById.put(currentElementId, type);
        }
        currentClass = context.getIndex().getClasses().get(currentElementId);
    }

    private void startAttribute(XMLStreamReader reader) {
        var id = XmiStreams.attribute(reader, XmiNames.IDREF);
        currentAttribute = context.getAttributesById().get(id);
        currentLiteral = context.getLiteralsById().get(id);
    }

    /** A {@code <documentation>} belongs to whatever is open around it. */
    private void setDocumentation(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (openNow()) {
            case CONNECTOR_END -> currentEnd.documentation = value;
            case ATTRIBUTE -> currentAttribute.setDocumentation(value);
            case LITERAL -> currentLiteral.setDocumentation(value);
            default -> {
                // A connector and a class are documented through their <properties> instead.
            }
        }
    }

    /**
     * A {@code <properties>} says something different about each thing it can sit in, the element
     * name being the same for all of them: what kind of connector this is, whether an attribute's
     * default value can be changed, or a class's documentation and whether it is abstract.
     */
    private void applyProperties(XMLStreamReader reader) {
        switch (openNow()) {
            case CONNECTOR -> currentConnectorType = XmiStreams.attribute(reader, XmiNames.EA_TYPE);
            case ATTRIBUTE ->
                    currentAttribute.setFixed(
                            "frozen".equals(XmiStreams.attribute(reader, XmiNames.CHANGEABILITY)));
            case CLASS -> applyClassProperties(reader);
            default -> {
                // A connector end and an enum literal say nothing here.
            }
        }
    }

    private void applyClassProperties(XMLStreamReader reader) {
        var documentation = XmiStreams.attribute(reader, XmiNames.DOCUMENTATION);
        if (documentation != null && !documentation.isBlank()) {
            currentClass.setDocumentation(documentation);
        }
        currentClass.setAbstract("true".equals(XmiStreams.attribute(reader, XmiNames.IS_ABSTRACT)));
    }

    /**
     * What the walk is inside of, which is what decides who an element that can sit in several
     * places is talking about. It is read off the cursors rather than kept beside them, so that it
     * cannot come to disagree with them.
     *
     * <p>The order is the nesting of the file: an end sits in a connector, an attribute and a
     * literal sit in an element, and the element is the outermost of them.
     */
    private Open openNow() {
        if (currentEnd != null) {
            return Open.CONNECTOR_END;
        }
        if (currentConnectorId != null) {
            return Open.CONNECTOR;
        }
        if (currentAttribute != null) {
            return Open.ATTRIBUTE;
        }
        if (currentLiteral != null) {
            return Open.LITERAL;
        }
        return currentClass == null ? Open.NOTHING : Open.CLASS;
    }

    private void startConnector(XMLStreamReader reader) {
        currentConnectorId = XmiStreams.attribute(reader, XmiNames.IDREF);
        currentConnectorType = null;
        source = null;
        target = null;
    }

    /**
     * The class an end sits on is identified on the end itself; its {@code model} child names the
     * class but repeats no id, so reading the id from there leaves every end unattached.
     */
    private static ConnectorEnd endAt(XMLStreamReader reader) {
        var end = new ConnectorEnd();
        end.classId = XmiStreams.attribute(reader, XmiNames.IDREF);
        return end;
    }

    private void applyEndModel(XMLStreamReader reader) {
        if (currentEnd != null) {
            currentEnd.className = XmiStreams.attribute(reader, XmiNames.NAME);
        }
    }

    private void applyEndRole(XMLStreamReader reader) {
        if (currentEnd != null) {
            currentEnd.roleName = XmiStreams.attribute(reader, XmiNames.NAME);
        }
    }

    private void applyEndType(XMLStreamReader reader) {
        if (currentEnd == null) {
            return;
        }
        currentEnd.multiplicity =
                XmiBounds.normaliseMultiplicity(
                        XmiStreams.attribute(reader, XmiNames.MULTIPLICITY));
        currentEnd.aggregation = XmiStreams.attribute(reader, XmiNames.AGGREGATION);
    }

    private void applyEndStyle(XMLStreamReader reader) {
        if (currentEnd != null) {
            currentEnd.navigable =
                    XmiStyles.isNavigable(XmiStreams.attribute(reader, XmiNames.VALUE));
        }
    }

    /**
     * Both halves of a pair come from the one connector, and an aggregation counts as an
     * association: dropping it leaves a profile short of its release.
     */
    private void endConnector() {
        if (source == null || target == null || !isAssociation()) {
            resetConnector();
            return;
        }
        context.getIndex().getAssociationEnds().add(endOf(source, target));
        context.getIndex().getAssociationEnds().add(endOf(target, source));
        resetConnector();
    }

    private boolean isAssociation() {
        return XmiNames.EA_ASSOCIATION.equals(currentConnectorType)
                || XmiNames.EA_AGGREGATION.equals(currentConnectorType);
    }

    private XmiAssociationEnd endOf(ConnectorEnd end, ConnectorEnd opposite) {
        return new XmiAssociationEnd(
                currentConnectorId,
                end.classId,
                end.className,
                end.roleName,
                opposite.classId,
                opposite.className,
                opposite.roleName,
                end.multiplicity,
                end.navigable,
                end.aggregation,
                end.documentation);
    }

    private void resetConnector() {
        currentConnectorId = null;
        currentConnectorType = null;
        source = null;
        target = null;
    }

    /** What the walk is currently inside of; see {@link #openNow()}. */
    private enum Open {
        CONNECTOR,
        CONNECTOR_END,
        /** An {@code <attribute>} that the model section wrote as an {@code <ownedAttribute>}. */
        ATTRIBUTE,
        /** An {@code <attribute>} that the model section wrote as an {@code <ownedLiteral>}. */
        LITERAL,
        CLASS,
        NOTHING
    }

    /** What has been read of one end of a connector so far. */
    private static final class ConnectorEnd {
        private String classId;
        private String className;
        private String roleName;
        private String multiplicity;
        private boolean navigable;
        private String aggregation;
        private String documentation;
    }
}
