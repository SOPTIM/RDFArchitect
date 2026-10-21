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
import java.util.Set;

import javax.xml.stream.XMLStreamReader;

/**
 * Reads an Enterprise Architect export into an {@link XmiIndex}.
 *
 * <p>Three parts of the file have to be read together, and none of them is sufficient alone. The
 * {@code uml:Model} section holds the packages, classes, attributes and their bounds; the vendor
 * extension holds the documentation, the real element types and the association ends; and the
 * stereotype applications at the root of the file hold the only complete stereotype set. They are
 * joined over {@code xmi:id} in one streaming pass, because a full model reaches 100 MB.
 *
 * <p>Each part has a reader of its own, and this one hands every element to whichever part the walk
 * is in. What those readers have between them is the {@link XmiReadContext}; everything else is a
 * cursor into one part and stays with that part.
 */
public final class XmiReader {

    /** The {@code xmi:type} values of an extension entry that is really a class. */
    private static final Set<String> CLASSIFIER_TYPES =
            Set.of(XmiNames.UML_CLASS, XmiNames.UML_ENUMERATION);

    private final XmiReadContext context = new XmiReadContext();
    private final XmiModelReader model = new XmiModelReader(context);
    private final XmiExtensionReader extension = new XmiExtensionReader(context);
    private final XmiStereotypeReader stereotypes = new XmiStereotypeReader(context);
    private final XmiDiagramReader diagrams = new XmiDiagramReader(context);

    private XmiReader() {}

    /**
     * Reads one Enterprise Architect export.
     *
     * @param xmi the file's bytes, read once and not closed
     * @throws XmiReadException when the file is not readable XML
     */
    public static XmiIndex read(InputStream xmi) {
        var reader = new XmiReader();
        XmiStreams.walk(xmi, reader::startElement, reader::endElement);
        reader.join();
        return reader.context.getIndex();
    }

    /**
     * The UMLDI diagrams sit inside the model section, and its two readers share no element name,
     * so both are offered every element and each takes what it knows.
     */
    private void startElement(XMLStreamReader reader, Section section) {
        switch (section) {
            case MODEL -> {
                model.start(reader);
                diagrams.startInModel(reader);
            }
            case EXTENSION -> extension.start(reader);
            case DIAGRAMS -> diagrams.startInDiagrams(reader);
            case ROOT -> stereotypes.start(reader);
        }
    }

    private void endElement(String element, Section section) {
        switch (section) {
            case MODEL -> {
                model.end(element);
                diagrams.endInModel(element);
            }
            case EXTENSION -> extension.end(element);
            case DIAGRAMS -> diagrams.endInDiagrams(element);
            case ROOT -> {
                // A stereotype application is one empty element and ends where it started.
            }
        }
    }

    /**
     * Puts together what only becomes knowable once the whole file has been read.
     *
     * <p>Dropping comes first: the model part calls things classes that the extension knows better
     * about, writing {@code uml:Boundary} and {@code uml:Text} as {@code uml:Class}. One extension
     * entry carries no type at all, so a missing entry is tolerated and a name no URI can hold is
     * the backstop. The profiles are worked out afterwards, over what survived that.
     */
    private void join() {
        var index = context.getIndex();
        index.getClasses()
                .values()
                .removeIf(
                        cimClass -> {
                            var type = extension.getTypesById().get(cimClass.getId());
                            return (type != null && !CLASSIFIER_TYPES.contains(type))
                                    || isUnusableName(cimClass.getName());
                        });
        index.getProfiles().addAll(XmiProfiles.of(index));
        index.getPrefixes().putAll(context.getPrefixTable().paired());
    }

    private static boolean isUnusableName(String name) {
        return name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace);
    }

    /** Which part of the file the walk is in. */
    enum Section {
        MODEL,
        EXTENSION,
        /** The extension's own diagram list, which is a section within it. */
        DIAGRAMS,
        ROOT
    }
}
