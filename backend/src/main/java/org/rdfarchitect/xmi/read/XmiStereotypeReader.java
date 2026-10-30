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

import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamReader;

/**
 * Reads the stereotype applications at the root of the file, one empty element per application.
 *
 * <p>This is the only place an element's complete set of stereotypes is written. The extension's
 * own {@code stereotype} attributes hold one value each and lose the co-occurring ones the releases
 * do emit, so they are not read at all.
 *
 * <p>An application carries no state: it names the element it applies to and ends where it started.
 * The element name is the stereotype; the attribute says what it applies to.
 *
 * <pre>{@code
 * <thecustomprofile:Entsoe base_Class="EAID_1"/>           onto a class
 * <thecustomprofile:enumeration base_Enumeration="EAID_4"/>  onto an enumeration
 * <thecustomprofile:deprecated base_Property="EAID_2"/>    onto a UML attribute
 * <thecustomprofile:NC base_Association="EAID_7"/>         onto an association, so onto both halves
 * }</pre>
 *
 * <p>Every element at the root of the file arrives here, not only an application, so one that names
 * nothing this import knows simply does nothing.
 *
 * @see org.rdfarchitect.xmi.read
 */
@RequiredArgsConstructor
final class XmiStereotypeReader {

    /**
     * Bookkeeping that the shape of the element does not give away. Everything else Enterprise
     * Architect records this way does; see {@link #statesAValue}.
     */
    private static final Set<String> BOOKKEEPING = Set.of(XmiNames.IS_BASED_ON);

    private final XmiReadContext context;

    void start(XMLStreamReader reader) {
        var stereotype = reader.getLocalName();
        if (BOOKKEEPING.contains(stereotype) || statesAValue(reader, stereotype)) {
            return;
        }
        var classes = context.getIndex().getClasses();
        var onClass = classes.get(XmiStreams.attribute(reader, XmiNames.BASE_CLASS));
        var onEnumeration = classes.get(XmiStreams.attribute(reader, XmiNames.BASE_ENUMERATION));
        var onAttribute =
                context.getAttributesById()
                        .get(XmiStreams.attribute(reader, XmiNames.BASE_PROPERTY));

        if (onClass != null) {
            onClass.getStereotypes().add(stereotype);
        }
        if (onEnumeration != null) {
            onEnumeration.getStereotypes().add(stereotype);
        }
        if (onAttribute != null) {
            onAttribute.getStereotypes().add(stereotype);
        }
        applyToAssociation(reader, stereotype);
    }

    /**
     * Whether an application is Enterprise Architect recording a value rather than applying a
     * stereotype.
     *
     * <p>The two look alike and are told apart by their shape: a stereotype says only what it
     * applies to, while a recorded value carries an attribute named after the element holding it —
     * {@code <thecustomprofile:date_created base_Class="…" date_created="2008-05-08 14:13:18"/>}.
     * Measured across a released full model the rule separates them exactly: all nine of {@code
     * GUIDBasedOn}, {@code CIM_IsRdfAbout}, {@code Profile}, {@code tagged}, {@code style}, {@code
     * package_name}, {@code date_created}, {@code tpos} and {@code date_modified} carry one, and
     * none of the eleven stereotypes a release does emit does. Only {@code IsBasedOn} hides, which
     * is why it is named instead.
     */
    private boolean statesAValue(XMLStreamReader reader, String stereotype) {
        return XmiStreams.attribute(reader, stereotype) != null;
    }

    /**
     * An association is stereotyped once and consists of two halves, so both take the value on. The
     * halves exist by now: they are made at the end of the connector, and the applications come
     * after the whole extension.
     */
    private void applyToAssociation(XMLStreamReader reader, String stereotype) {
        var connectorId = XmiStreams.attribute(reader, XmiNames.BASE_ASSOCIATION);
        if (connectorId == null) {
            return;
        }
        for (var end : context.getEndsByConnectorId().getOrDefault(connectorId, List.of())) {
            end.getStereotypes().add(stereotype);
        }
    }
}
