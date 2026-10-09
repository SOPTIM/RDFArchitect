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

package org.rdfarchitect.shacl.dto;

import lombok.Builder;
import lombok.Data;

/**
 * One term a workspace's CIM schema declares, in the form an editor needs to offer it.
 *
 * <p>The IRI is split rather than abbreviated because the prefix a document uses for a namespace is
 * the document's own business: two constraints files may bind {@code cim:} differently, and one may
 * not bind it at all. The client pairs {@link #namespace} with whatever the open document declares.
 */
@Data
@Builder
public class SchemaTerm {

    /** What the schema declares the term as. */
    public enum Kind {
        CLASS,
        PROPERTY,
        /** A member of an enumerated class, e.g. {@code cim:UnitSymbol.W}. */
        ENUM_MEMBER
    }

    private Kind kind;

    private String iri;

    /** Everything up to and including the final {@code #} or {@code /}. */
    private String namespace;

    private String localName;

    /**
     * The term's {@code rdfs:label}, but only when it says more than {@link #localName} does.
     *
     * <p>CIM labels usually repeat the local name, and a workspace has thousands of terms, so
     * repeating it would be most of the response for none of the information.
     */
    private String label;

    /**
     * The class a property is declared on, so a form can offer a class's own properties first.
     *
     * <p>Only set for {@link Kind#PROPERTY}. A property declared on several classes reports the
     * first; the form uses this to order a list, not to decide what is allowed.
     */
    private String domain;
}
