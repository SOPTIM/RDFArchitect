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

package org.rdfarchitect.models.cim.data.dto.facade;

import org.apache.jena.graph.Graph;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;

/**
 * Writes the resources of a CIM profile graph.
 *
 * <h2>Creates only</h2>
 *
 * There is no replace and no delete here. The one caller builds a graph from nothing, which is the
 * simplest shape a write can have, and designing the rest without a caller would be guesswork:
 * {@link org.rdfarchitect.models.cim.queries.update.CIMUpdates} also knows the wildcard delete of
 * an association's two halves, the blank-node value wrappers and the uuid of a resource that is
 * only referenced, none of which an import runs into.
 *
 * <p>What does hold is that <strong>each method writes the complete triple set of its
 * resource</strong>, never part of one. That is the property a replace needs later: delete the
 * resource, call the same method again. A writer that wrote incrementally could not be reused that
 * way.
 *
 * <h2>Why this is not the read contract</h2>
 *
 * {@link ICIMModelReader} is a separate interface on purpose, so that the rendering path can ask
 * for a graph it is unable to change and get a guarantee instead of a convention. Both are meant to
 * end up on one object over one model; keeping the contracts apart is what makes that safe.
 *
 * <h2>What it writes beyond what an edit writes</h2>
 *
 * A package's parent and an association's stereotypes. {@code CIMUpdates} writes neither, so a
 * graph written here carries two things the editing path drops on the first save. That is
 * deliberate and recorded as a defect, not an accident of this interface.
 */
public interface ICIMModelWriter {

    /** Writes a package, including its parent package where it has one. */
    void writePackage(CIMPackage cimPackage);

    /** Writes a class or an enumeration; which of the two is decided by its stereotypes. */
    void writeClass(CIMClass cimClass);

    /** Writes one entry of an enumeration, typed by the enumeration it belongs to. */
    void writeEnumEntry(CIMEnumEntry enumEntry);

    /** Writes an attribute of a class. */
    void writeAttribute(CIMAttribute attribute);

    /** Writes both halves of an association, which carry each other's inverse role name. */
    void writeAssociationPair(CIMAssociationPair pair);

    /**
     * Binds a namespace prefix. Profile recognition depends on a bound {@code cim} prefix without
     * saying so, which is why this belongs to writing a profile and not to the caller.
     */
    void bindPrefix(String prefix, String namespace);

    /** Writes the ontology header of the profile. */
    void writeOntology(OntologyDTO ontology);

    /** The graph that was written, for the caller to hand to the store. */
    Graph graph();
}
