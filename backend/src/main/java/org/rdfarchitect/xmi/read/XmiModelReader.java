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
 * Reads the {@code uml:Model} section: the packages and what they hold, the attributes with their
 * types and bounds, the enum literals and the superclasses.
 *
 * <p>This is the only part that creates anything. The other two find what it made by id, which is
 * why the attributes and literals go into {@link XmiReadContext} as well as onto their class.
 *
 * <p>Packages nest, so they are kept on a stack; the kinds are kept on a second one because an end
 * element says only that a packaged element closed, not which of the three it was.
 *
 * <pre>{@code
 * <packagedElement xmi:type="uml:Package" xmi:id="EAPK_1" name="Topology">
 *   <packagedElement xmi:type="uml:Class" xmi:id="EAID_1" name="TopologicalNode">
 *     <generalization general="EAID_9"/>                    rememberSuperClass
 *     <ownedAttribute xmi:id="EAID_2" name="pInjection">    startOwnedAttribute
 *       <type xmi:idref="EAID_8"/>                          setAttributeType
 *       <lowerValue value="1"/>                             setLowerBound
 *       <upperValue value="1"/>                             setUpperBound
 *       <defaultValue value="0"/>                           setDefaultValue
 *     </ownedAttribute>
 *     <ownedAttribute xmi:id="EAID_3" association="EAID_7"/>  an association end, skipped
 *   </packagedElement>
 *   <packagedElement xmi:type="uml:Enumeration" xmi:id="EAID_4" name="Currency">
 *     <ownedLiteral xmi:id="EAID_5" name="EUR"/>            startOwnedLiteral
 *   </packagedElement>
 * </packagedElement>
 * }</pre>
 *
 * @see org.rdfarchitect.xmi.read
 */
@RequiredArgsConstructor
final class XmiModelReader {

    private final XmiReadContext context;

    private final Deque<XmiPackage> packageStack = new ArrayDeque<>();
    private final Deque<Kind> packagedElements = new ArrayDeque<>();

    private XmiClass currentClass;
    private XmiAttribute currentAttribute;

    void start(XMLStreamReader reader) {
        switch (reader.getLocalName()) {
            case XmiNames.PACKAGED_ELEMENT -> startPackagedElement(reader);
            case XmiNames.OWNED_ATTRIBUTE -> startOwnedAttribute(reader);
            case XmiNames.OWNED_LITERAL -> startOwnedLiteral(reader);
            case XmiNames.GENERALIZATION ->
                    rememberSuperClass(XmiStreams.attribute(reader, XmiNames.GENERAL));
            case XmiNames.TYPE -> setAttributeType(XmiStreams.attribute(reader, XmiNames.IDREF));
            case XmiNames.LOWER_VALUE ->
                    setLowerBound(XmiStreams.attribute(reader, XmiNames.VALUE));
            case XmiNames.UPPER_VALUE ->
                    setUpperBound(XmiStreams.attribute(reader, XmiNames.VALUE));
            case XmiNames.DEFAULT_VALUE ->
                    setDefaultValue(XmiStreams.attribute(reader, XmiNames.VALUE));
            default -> {
                // Comments, constraints and the rest carry nothing an import reads.
            }
        }
    }

    void end(String element) {
        switch (element) {
            case XmiNames.PACKAGED_ELEMENT -> endPackagedElement();
            case XmiNames.OWNED_ATTRIBUTE -> currentAttribute = null;
            default -> {
                // See start.
            }
        }
    }

    /**
     * A {@code packagedElement} is only one of the three this reads when its type says so. A
     * partial export may leave the type out, which makes it none of them rather than an error: the
     * scanner takes the same view, and the two passes have to agree on what a file holds.
     */
    private void startPackagedElement(XMLStreamReader reader) {
        var id = XmiStreams.attribute(reader, XmiNames.ID);
        var name = XmiStreams.attribute(reader, XmiNames.NAME);
        var type = XmiStreams.attribute(reader, XmiNames.TYPE);
        switch (type == null ? "" : type) {
            case XmiNames.UML_PACKAGE -> {
                packagedElements.push(Kind.PACKAGE);
                var parent = packageStack.peek();
                var created = new XmiPackage(id, parent == null ? null : parent.getId(), name);
                context.getIndex().getPackages().put(id, created);
                packageStack.push(created);
            }
            case XmiNames.UML_CLASS, XmiNames.UML_ENUMERATION -> {
                packagedElements.push(Kind.CLASS);
                var owner = packageStack.peek();
                currentClass = new XmiClass(id, name, owner == null ? null : owner.getId());
                currentClass.setEnumeration(XmiNames.UML_ENUMERATION.equals(type));
                context.getIndex().getClasses().put(id, currentClass);
            }
            default -> packagedElements.push(Kind.OTHER);
        }
    }

    private void endPackagedElement() {
        var kind = packagedElements.poll();
        if (kind == Kind.PACKAGE) {
            packageStack.poll();
        } else if (kind == Kind.CLASS) {
            currentClass = null;
        }
    }

    /**
     * An {@code ownedAttribute} carrying an {@code association} is one end of an association and
     * not an attribute; the ends are read from the connectors instead.
     */
    private void startOwnedAttribute(XMLStreamReader reader) {
        if (currentClass == null || XmiStreams.attribute(reader, XmiNames.ASSOCIATION) != null) {
            return;
        }
        var id = XmiStreams.attribute(reader, XmiNames.ID);
        currentAttribute = new XmiAttribute(id, XmiStreams.attribute(reader, XmiNames.NAME));
        currentClass.getAttributes().add(currentAttribute);
        context.getAttributesById().put(id, currentAttribute);
    }

    private void startOwnedLiteral(XMLStreamReader reader) {
        if (currentClass == null) {
            return;
        }
        var literal =
                new XmiEnumLiteral(
                        XmiStreams.attribute(reader, XmiNames.ID),
                        XmiStreams.attribute(reader, XmiNames.NAME));
        currentClass.getLiterals().add(literal);
        context.getLiteralsById().put(literal.getId(), literal);
    }

    private void rememberSuperClass(String generalId) {
        if (currentClass != null && generalId != null) {
            currentClass.getSuperClassIds().add(generalId);
        }
    }

    private void setAttributeType(String typeId) {
        if (currentAttribute != null) {
            currentAttribute.setTypeId(typeId);
        }
    }

    private void setLowerBound(String value) {
        if (currentAttribute != null) {
            currentAttribute.setLowerBound(value);
        }
    }

    private void setUpperBound(String value) {
        if (currentAttribute != null) {
            currentAttribute.setUpperBound(XmiBounds.normaliseUpperBound(value));
        }
    }

    /**
     * The default value as the export states it, untidied. One CGMES 2.4.15 profile URI is written
     * with a leading space and its release keeps it, so trimming here would quietly make the two
     * disagree; it is trimmed where such a value has to become a URI instead, which cannot hold
     * one.
     */
    private void setDefaultValue(String value) {
        if (currentAttribute != null && value != null) {
            currentAttribute.setDefaultValue(value);
        }
    }

    /** What a {@code packagedElement} turned out to be, remembered until it closes again. */
    private enum Kind {
        PACKAGE,
        CLASS,
        OTHER
    }
}
