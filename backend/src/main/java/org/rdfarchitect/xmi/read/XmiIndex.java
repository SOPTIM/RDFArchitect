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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything one Enterprise Architect export holds, as far as an import reads it. */
@Getter
public class XmiIndex {

    /** The attribute that makes a metadata class the header of its profile. */
    private static final String VERSION_IRI = "versionIRI";

    private final Map<String, XmiPackage> packages = new LinkedHashMap<>();

    private final Map<String, XmiClass> classes = new LinkedHashMap<>();

    private final List<XmiAssociationEnd> associationEnds = new ArrayList<>();

    /** The class diagrams of the export, by {@code xmi:id}. */
    private final Map<String, XmiDiagram> diagrams = new LinkedHashMap<>();

    /**
     * The datatypes Enterprise Architect keeps of its own, by {@code xmi:id}. They sit outside the
     * model section and are referenced by attributes like any other type, so without them a CGMES
     * 3.0 ontology header's twenty attributes resolve to nothing.
     *
     * <p>Kept as names rather than added to {@link #classes}: a model commonly declares a class of
     * the same name — {@code String} is both an Enterprise Architect primitive and a CIM datatype —
     * and two definitions under one URI would write each other's triples.
     */
    private final Map<String, String> primitiveTypeNames = new LinkedHashMap<>();

    /**
     * What the export refers to but does not contain, by {@code xmi:id}. Only a partial export has
     * any: Enterprise Architect writes an {@code EAStub} per missing target, carrying its name and
     * nothing else.
     *
     * <p>A stub is enough to say what is missing and not enough to write it. It carries no
     * stereotypes, so neither the namespace nor the choice between {@code cims:dataType} and {@code
     * rdfs:range} follows from it, and the two cannot be guessed from the name: the stubs of one
     * export hold datatypes and enumerations side by side.
     */
    private final Map<String, String> stubNames = new LinkedHashMap<>();

    /** The packages that hold a profile, in the order the model declares them. */
    private final List<XmiProfile> profiles = new ArrayList<>();

    /**
     * The namespace prefix table of the export, prefix to namespace, empty when it states none.
     * CGMES 3.0 carries one and CGMES 2.4.15 does not, so an import cannot rely on it being there.
     * It is also not authoritative: the released 3.0 table has three entries the release itself
     * does not use.
     */
    private final Map<String, String> prefixes = new LinkedHashMap<>();

    /**
     * Every package from {@code packageId} up to the outermost one, nearest first.
     *
     * @return an empty list when the package is unknown, which a partial export makes possible
     */
    public List<XmiPackage> ancestryOf(String packageId) {
        var ancestry = new ArrayList<XmiPackage>();
        var current = packages.get(packageId);
        while (current != null && !ancestry.contains(current)) {
            ancestry.add(current);
            current = packages.get(current.getParentId());
        }
        return ancestry;
    }

    /**
     * The profile metadata of a package, or a stand-in holding nothing but its id and name.
     *
     * <p>Any package can be written as a schema. One that declares a profile brings a namespace of
     * its own along; one that does not is written the same way without it, which is also what every
     * CGMES 2.4.15 profile does.
     */
    public XmiProfile schemaOf(String packageId) {
        for (var profile : profiles) {
            if (profile.id().equals(packageId)) {
                return profile;
            }
        }
        var cimPackage = packages.get(packageId);
        if (cimPackage == null) {
            throw new IllegalArgumentException("The export holds no package " + packageId + ".");
        }
        return new XmiProfile(packageId, cimPackage.getName(), List.of(), List.of());
    }

    /**
     * The classes of {@code profile} that describe it rather than belong to it, which is the {@code
     * <Profile>_Ontology} of a CGMES 3.0 profile.
     *
     * <p>Such a class becomes the ontology header and is not written as a class, so it is also not
     * a starting point of the closure: its attribute types and its superclass describe the header,
     * not the profile, and following them pulls a package of datatypes and the configuration
     * classes into a schema that has nothing to point at them.
     *
     * <p>Recognised by stating a {@code versionIRI} with a value. That is the same attribute the
     * profile namespace is derived from, so a class recognised here is one the header can be
     * written for.
     *
     * @return the ids, empty for the CGMES 2.4.15 convention, which states a {@code baseURI} and a
     *     {@code shortName} instead and keeps its metadata class a class
     */
    public Set<String> headerClassIdsOf(XmiProfile profile) {
        var headers = new LinkedHashSet<String>();
        for (var metadataClassId : profile.metadataClassIds()) {
            var metadataClass = classes.get(metadataClassId);
            if (metadataClass != null && describesItsProfile(metadataClass)) {
                headers.add(metadataClassId);
            }
        }
        return headers;
    }

    private boolean describesItsProfile(XmiClass cimClass) {
        return cimClass.getAttributes().stream()
                .anyMatch(
                        attribute ->
                                VERSION_IRI.equals(attribute.getName())
                                        && attribute.getDefaultValue() != null
                                        && !attribute.getDefaultValue().isBlank());
    }

    /** Whether {@code packageId} is {@code ancestorId} or sits anywhere below it. */
    public boolean isWithin(String packageId, String ancestorId) {
        return ancestryOf(packageId).stream()
                .anyMatch(ancestor -> ancestor.getId().equals(ancestorId));
    }
}
