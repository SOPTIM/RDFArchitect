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

/**
 * The vocabulary of an Enterprise Architect export, in one place because two readers walk the same
 * file and were spelling the same names twice.
 *
 * <p>A misspelled name does not fail: an attribute that is not there reads as {@code null} and an
 * element that is not matched falls to a default branch, so the import would quietly carry less
 * than the file holds. Naming them here turns that into a compile error.
 *
 * <p>Every value is the local name as the file writes it. Namespaces are not part of it, because an
 * export declares two {@code xmi} namespaces and writes the same name under either.
 *
 * <p>Element names and XML attribute names are kept apart below, since a file uses several of them
 * for both. "Attribute" is the word that carries the most meanings: a UML attribute of a class is
 * an {@code <ownedAttribute>} in the model section and an {@code <attribute>} in the extension,
 * while an XML attribute is what {@link XmiStreams#attribute} reads and what the last two groups
 * name.
 */
final class XmiNames {

    private XmiNames() {}

    // -------------------------------------------------------------------------
    // The sections a file is divided into
    // -------------------------------------------------------------------------

    static final String MODEL_SECTION = "Model";
    static final String EXTENSION_SECTION = "Extension";

    // -------------------------------------------------------------------------
    // Elements of the model section
    // -------------------------------------------------------------------------

    static final String PACKAGED_ELEMENT = "packagedElement";
    static final String OWNED_ATTRIBUTE = "ownedAttribute";
    static final String OWNED_LITERAL = "ownedLiteral";
    static final String GENERALIZATION = "generalization";
    static final String LOWER_VALUE = "lowerValue";
    static final String UPPER_VALUE = "upperValue";
    static final String DEFAULT_VALUE = "defaultValue";

    // -------------------------------------------------------------------------
    // Elements of the vendor extension
    // -------------------------------------------------------------------------

    static final String ELEMENT = "element";
    static final String PROPERTIES = "properties";
    static final String CONNECTOR = "connector";
    static final String SOURCE = "source";
    static final String TARGET = "target";
    static final String ROLE = "role";
    static final String STYLE = "style";

    /**
     * The {@code <attribute>} of an extension element, which is a UML attribute of a class and has
     * nothing to do with an XML attribute. {@link XmiStreams#attribute} reads the latter.
     */
    static final String ATTRIBUTE = "attribute";

    /** The {@code <model>} of a connector end, which is not the model section. */
    static final String END_MODEL = "model";

    // -------------------------------------------------------------------------
    // Names the file uses for an element and for an XML attribute alike
    // -------------------------------------------------------------------------

    /** The {@code <type>} of a UML attribute, and the {@code type} of a packaged element. */
    static final String TYPE = "type";

    /** The {@code <documentation>} of an element, and the {@code documentation} of a connector. */
    static final String DOCUMENTATION = "documentation";

    // -------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------

    static final String ID = "id";
    static final String IDREF = "idref";
    static final String NAME = "name";
    static final String VALUE = "value";
    static final String GENERAL = "general";
    static final String ASSOCIATION = "association";
    static final String AGGREGATION = "aggregation";
    static final String MULTIPLICITY = "multiplicity";
    static final String IS_ABSTRACT = "isAbstract";
    static final String CHANGEABILITY = "changeability";
    static final String EA_TYPE = "ea_type";
    static final String BASE_PROPERTY = "base_Property";

    // -------------------------------------------------------------------------
    // Values of the type attribute
    // -------------------------------------------------------------------------

    static final String UML_PACKAGE = "uml:Package";
    static final String UML_CLASS = "uml:Class";
    static final String UML_ENUMERATION = "uml:Enumeration";

    // -------------------------------------------------------------------------
    // Values of the vendor extension
    // -------------------------------------------------------------------------

    /** The {@code ea_type} of a connector that carries an association; both halves count. */
    static final String EA_ASSOCIATION = "Association";

    static final String EA_AGGREGATION = "Aggregation";

    /** What a stereotype application names the element it applies to. */
    static final String BASE_CLASS = "base_Class";

    static final String BASE_ENUMERATION = "base_Enumeration";

    /** Stereotype applications that are UML bookkeeping rather than a stereotype of the model. */
    static final String GUID_BASED_ON = "GUIDBasedOn";

    static final String IS_BASED_ON = "IsBasedOn";
}
