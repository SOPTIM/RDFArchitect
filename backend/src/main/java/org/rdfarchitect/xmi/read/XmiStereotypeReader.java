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
 * <thecustomprofile:NC base_Association="EAID_7"/>         onto an association, not read today
 * }</pre>
 *
 * <p>Every element at the root of the file arrives here, not only an application, so one that names
 * nothing this import knows simply does nothing.
 *
 * @see org.rdfarchitect.xmi.read
 */
@RequiredArgsConstructor
final class XmiStereotypeReader {

    /** Applications that are UML bookkeeping rather than a stereotype of the model. */
    private static final Set<String> BOOKKEEPING =
            Set.of(XmiNames.GUID_BASED_ON, XmiNames.IS_BASED_ON);

    private final XmiReadContext context;

    void start(XMLStreamReader reader) {
        var stereotype = reader.getLocalName();
        if (BOOKKEEPING.contains(stereotype)) {
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
    }
}
