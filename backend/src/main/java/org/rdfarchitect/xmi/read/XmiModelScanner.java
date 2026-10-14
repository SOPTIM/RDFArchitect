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

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the {@code uml:Model} section of an Enterprise Architect export and reports the profiles it
 * holds.
 *
 * <p>The export is walked as a stream and never held as a document: a full model reaches 100 MB,
 * which no tree representation survives. The encoding is the file's own — the exports are {@code
 * windows-1252} — and is taken from the XML declaration, which is why the reader is handed a byte
 * stream rather than a reader.
 *
 * <p>Which packages count is {@link XmiProfiles}'s to say. This pass exists beside {@link
 * XmiReader} because listing the profiles of a file must not cost reading all of it: the walk stops
 * at the end of the model section, which is why the 97 MB CGMES 3.0 full model is scanned in under
 * 250 ms.
 */
public final class XmiModelScanner {

    private final Deque<PackageFrame> packages = new ArrayDeque<>();
    private final Deque<Kind> packagedElements = new ArrayDeque<>();
    private final List<XmiProfile> profiles = new ArrayList<>();

    private List<String> classNamespaceCandidates;
    private String attributeName;

    private XmiModelScanner() {}

    /**
     * Scans one export.
     *
     * @param xmi the file's bytes, which the scanner reads once and does not close
     * @return the profiles the export holds, in the order the model declares them
     * @throws XmiReadException when the file is not readable XML
     */
    public static List<XmiProfile> scan(InputStream xmi) {
        return new XmiModelScanner().read(xmi);
    }

    private List<XmiProfile> read(InputStream xmi) {
        XMLStreamReader reader = null;
        try {
            reader = inputFactory().createXMLStreamReader(xmi);
            walk(reader);
            return List.copyOf(profiles);
        } catch (XMLStreamException exception) {
            throw new XmiReadException("The file is not a readable XMI xmi.", exception);
        } finally {
            closeQuietly(reader);
        }
    }

    private void walk(XMLStreamReader reader) throws XMLStreamException {
        var insideModel = false;
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    var element = reader.getLocalName();
                    if (XmiNames.MODEL_SECTION.equals(element)) {
                        insideModel = true;
                    } else if (insideModel) {
                        startInsideModel(reader, element);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    var element = reader.getLocalName();
                    if (XmiNames.MODEL_SECTION.equals(element)) {
                        // Everything after the model section is the vendor extension, which this
                        // pass has no use for, so the walk stops rather than reading on.
                        return;
                    }
                    if (insideModel) {
                        endInsideModel(element);
                    }
                }
                default -> {
                    // Text, comments and whitespace carry nothing this pass reads.
                }
            }
        }
    }

    private void startInsideModel(XMLStreamReader reader, String element) {
        switch (element) {
            case XmiNames.PACKAGED_ELEMENT -> startPackagedElement(reader);
            case XmiNames.OWNED_ATTRIBUTE ->
                    attributeName = XmiStreams.attribute(reader, XmiNames.NAME);
            case XmiNames.DEFAULT_VALUE ->
                    collectNamespaceCandidate(XmiStreams.attribute(reader, XmiNames.VALUE));
            default -> {
                // Comments, bounds, type references and the rest are not part of the skeleton.
            }
        }
    }

    private void endInsideModel(String element) {
        switch (element) {
            case XmiNames.PACKAGED_ELEMENT -> endPackagedElement();
            case XmiNames.OWNED_ATTRIBUTE -> attributeName = null;
            default -> {
                // See startInsideModel.
            }
        }
    }

    private void startPackagedElement(XMLStreamReader reader) {
        var type = XmiStreams.attribute(reader, XmiNames.TYPE);
        if (XmiNames.UML_PACKAGE.equals(type)) {
            packagedElements.push(Kind.PACKAGE);
            packages.push(
                    new PackageFrame(
                            XmiStreams.attribute(reader, XmiNames.ID),
                            XmiStreams.attribute(reader, XmiNames.NAME)));
            return;
        }
        if (XmiNames.UML_CLASS.equals(type)) {
            packagedElements.push(Kind.CLASS);
            classNamespaceCandidates = new ArrayList<>();
            return;
        }
        packagedElements.push(Kind.OTHER);
    }

    private void endPackagedElement() {
        var kind = packagedElements.poll();
        if (kind == Kind.PACKAGE) {
            packages.poll();
            return;
        }
        if (kind != Kind.CLASS) {
            return;
        }
        var candidates = classNamespaceCandidates;
        classNamespaceCandidates = null;
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        var owner = packages.peek();
        if (owner != null) {
            profiles.add(new XmiProfile(owner.id(), owner.name(), List.copyOf(candidates)));
        }
    }

    /**
     * Takes a default value as a profile URI when its attribute is named like one and the value is
     * an absolute IRI. The leading space one released profile puts in front of its URI is stripped
     * here, because the value is a URI everywhere downstream.
     */
    private void collectNamespaceCandidate(String value) {
        if (classNamespaceCandidates == null || attributeName == null || value == null) {
            return;
        }
        if (!XmiProfiles.statesProfileUri(attributeName)) {
            return;
        }
        var candidate = XmiProfiles.profileUriOrNull(value);
        if (candidate != null) {
            classNamespaceCandidates.add(candidate);
        }
    }

    /**
     * An export declares two {@code xmi} namespaces and reaches 100 MB, so attributes are read by
     * local name and nothing is coalesced or expanded.
     */
    private static XMLInputFactory inputFactory() {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
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

    private enum Kind {
        PACKAGE,
        CLASS,
        OTHER
    }

    private record PackageFrame(String id, String name) {}
}
