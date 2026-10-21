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

import org.rdfarchitect.xmi.read.XmiReader.Section;

import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Walks an Enterprise Architect export as a stream and says which part of it each element belongs
 * to.
 *
 * <p>The export is never held as a document: a full model reaches 100 MB, which no tree
 * representation survives. The encoding is the file's own — the exports are {@code windows-1252} —
 * and is taken from the XML declaration, which is why a byte stream is read rather than a reader.
 * Attributes are matched by local name, because an export declares two {@code xmi} namespaces.
 */
final class XmiStreams {

    /**
     * The JAXP ceilings on how much of a document may be accumulated, which an export exceeds. Set
     * to zero, which these properties spell "no limit"; see {@link #inputFactory()}.
     */
    private static final List<String> UNBOUNDED_LIMITS =
            List.of(
                    "jdk.xml.maxGeneralEntitySizeLimit",
                    "jdk.xml.totalEntitySizeLimit",
                    "jdk.xml.entityExpansionLimit");

    private XmiStreams() {}

    /**
     * Reads {@code xmi} once, calling {@code onStart} and {@code onEnd} for every element.
     *
     * @throws XmiReadException when the file is not readable XML
     */
    static void walk(
            InputStream xmi,
            BiConsumer<XMLStreamReader, Section> onStart,
            BiConsumer<String, Section> onEnd) {
        XMLStreamReader reader = null;
        try {
            reader = inputFactory().createXMLStreamReader(xmi);
            var section = Section.ROOT;
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        section = sectionEntered(reader.getLocalName(), section);
                        onStart.accept(reader, section);
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        var element = reader.getLocalName();
                        onEnd.accept(element, section);
                        section = sectionLeft(element, section);
                    }
                    default -> {
                        // Text, comments and whitespace carry nothing an import reads.
                    }
                }
            }
        } catch (XMLStreamException exception) {
            throw new XmiReadException("The file is not a readable XMI xmi.", exception);
        } finally {
            closeQuietly(reader);
        }
    }

    /**
     * The value of the attribute named {@code localName}, or {@code null} if there is none. A null
     * namespace is what matches the name alone, which is what an export needs: it declares two
     * {@code xmi} namespaces and writes the same attribute under either.
     */
    static String attribute(XMLStreamReader reader, String localName) {
        return reader.getAttributeValue(null, localName);
    }

    /**
     * The diagram list is a section within the extension rather than beside it, so it is only
     * entered from there — {@code <diagrams>} is a common enough name to meet elsewhere.
     */
    private static Section sectionEntered(String element, Section section) {
        return switch (element) {
            case XmiNames.MODEL_SECTION -> Section.MODEL;
            case XmiNames.EXTENSION_SECTION -> Section.EXTENSION;
            case XmiNames.DIAGRAMS_SECTION ->
                    section == Section.EXTENSION ? Section.DIAGRAMS : section;
            default -> section;
        };
    }

    private static Section sectionLeft(String element, Section section) {
        return switch (element) {
            case XmiNames.MODEL_SECTION, XmiNames.EXTENSION_SECTION -> Section.ROOT;
            case XmiNames.DIAGRAMS_SECTION ->
                    section == Section.DIAGRAMS ? Section.EXTENSION : section;
            default -> section;
        };
    }

    /**
     * A reader for an export: no DTD, no external entities, nothing coalesced, and no ceiling on
     * how much of the file may be accumulated.
     *
     * <p>That last part is the one that has to be said out loud. A JDK whose {@code
     * jaxp.properties} is the shipped strict template caps the accumulated entity size at 100,000,
     * and the 102 MB CGMES 3.0 export passes that around a third of the way in — it fails with
     * {@code JAXP00010003} rather than being read. The caps guard against entity expansion, which
     * {@code SUPPORT_DTD=false} has already made impossible here, so lifting them gives nothing up:
     * without a DTD no entity can be declared in the first place.
     *
     * <p>Lifting them is attempted rather than required, because an implementation that does not
     * know these properties also does not impose their limits.
     */
    static XMLInputFactory inputFactory() {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        for (var limit : UNBOUNDED_LIMITS) {
            try {
                factory.setProperty(limit, "0");
            } catch (IllegalArgumentException _) {
                // Not every implementation knows it, and one that does not imposes no limit.
            }
        }
        return factory;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException _) {
            // The stream is the caller's; failing to close the reader changes nothing for them.
        }
    }
}
