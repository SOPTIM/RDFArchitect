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

package org.rdfarchitect.xmi.map;

import lombok.Getter;

import org.rdfarchitect.xmi.read.XmiClass;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiProfile;

import java.util.List;
import java.util.Objects;

/**
 * The namespaces the resources of one profile get.
 *
 * <p>There are three of them and they are not the same. In a released profile nearly every class,
 * attribute and association half sits in the <em>model</em> namespace, the handful stereotyped
 * {@code Entsoe} or {@code European} in the <em>extension</em> namespace, and only the package and
 * the ontology in the <em>profile</em> namespace. Writing one where another belongs produces a
 * graph that loads and matches nothing.
 *
 * <p>Where each comes from differs per release, so each is looked for in both places:
 *
 * <table>
 *   <caption>Where a namespace is stated</caption>
 *   <tr><th></th><th>CGMES 3.0</th><th>CGMES 2.4.15</th></tr>
 *   <tr><td>model</td><td>prefix table, {@code cim}</td>
 *       <td>{@code namespaceUML} on a metadata class</td></tr>
 *   <tr><td>extension</td><td>prefix table, {@code eu}</td>
 *       <td>{@code namespaceURI}, full model only</td></tr>
 *   <tr><td>profile</td><td>{@code versionIRI}, version segment replaced by {@code #}</td>
 *       <td>none — its package lives in the model namespace</td></tr>
 * </table>
 *
 * <p>The extension namespace is the one that can be genuinely absent. A CGMES 2.4.15 profiles-only
 * export states it nowhere while still holding 45 elements that need it, and the release puts those
 * under {@code http://entsoe.eu/CIM/SchemaExtension/3/1#} — a value the file does not contain and
 * nothing in it implies. It is therefore left empty rather than guessed at, and whoever runs the
 * import has to supply it.
 */
@Getter
public final class XmiNamespaces {

    /** What the prefix table calls the namespace nearly every resource of a profile gets. */
    private static final String MODEL_PREFIX = "cim";

    /** What the prefix table calls the namespace the European extensions get. */
    private static final String EXTENSION_PREFIX = "eu";

    private static final String MODEL_ATTRIBUTE = "namespaceUML";
    private static final String EXTENSION_ATTRIBUTE = "namespaceURI";

    private static final String VERSION_IRI_ATTRIBUTE = "versionIRI";

    private final String model;
    private final String extension;
    private final String profile;

    private XmiNamespaces(String model, String extension, String profile) {
        this.model = model;
        this.extension = extension;
        this.profile = profile;
    }

    /**
     * Works out the namespaces of one profile.
     *
     * @throws XmiImportRefusal when the export states no model namespace, without which no resource
     *     of the profile can be given a URI
     */
    public static XmiNamespaces of(XmiIndex index, XmiProfile profile) {
        return of(index, profile, null);
    }

    /**
     * Works out the namespaces of one profile, with the extension namespace supplied from outside.
     *
     * @param suppliedExtension what to use where the export states no extension namespace of its
     *     own, or {@code null} to leave it absent
     * @throws XmiImportRefusal when the export states no model namespace
     */
    public static XmiNamespaces of(XmiIndex index, XmiProfile profile, String suppliedExtension) {
        var model = modelNamespace(index);
        if (model == null) {
            throw new XmiImportRefusal(
                    profile.name(),
                    "The export states no model namespace. It declares neither a 'cim' entry in a"
                            + " namespace prefix table nor a 'namespaceUML' attribute on one of its"
                            + " metadata classes, and without one no class can be given a URI.");
        }
        var stated = extensionNamespace(index);
        return new XmiNamespaces(
                model,
                stated != null ? stated : suppliedExtension,
                profileNamespace(index, profile));
    }

    /** Whether the elements stereotyped as extensions can be given a URI at all. */
    public boolean hasExtension() {
        return extension != null;
    }

    // -------------------------------------------------------------------------

    private static String modelNamespace(XmiIndex index) {
        var fromTable = index.getPrefixes().get(MODEL_PREFIX);
        return fromTable != null ? fromTable : defaultValueOf(index, MODEL_ATTRIBUTE);
    }

    private static String extensionNamespace(XmiIndex index) {
        var fromTable = index.getPrefixes().get(EXTENSION_PREFIX);
        return fromTable != null ? fromTable : defaultValueOf(index, EXTENSION_ATTRIBUTE);
    }

    /**
     * A namespace CGMES 2.4.15 states as the default value of an attribute, wherever in the export
     * it sits. It describes the model as a whole rather than the class declaring it, and which
     * class that is differs between the profiles-only and the full-model export.
     */
    private static String defaultValueOf(XmiIndex index, String attributeName) {
        for (var cimClass : index.getClasses().values()) {
            for (var attribute : cimClass.getAttributes()) {
                if (attributeName.equals(attribute.getName())
                        && isUsable(attribute.getDefaultValue())) {
                    return attribute.getDefaultValue().strip();
                }
            }
        }
        return null;
    }

    /**
     * The namespace of the profile itself, which only a {@code versionIRI} states. It is read from
     * the attribute by name rather than from the candidates the scan collected, because the two
     * conventions mean different things by their candidates and only this one is a namespace: CGMES
     * 2.4.15 offers a {@code baseURI}, which identifies the profile without being the namespace of
     * anything — its package resource lives in the model namespace.
     *
     * <p>One transformation on the way. {@code .../CoreEquipment-EU/3.0} names the version, and the
     * namespace is what is left once that segment is replaced by a {@code #}.
     *
     * @return the namespace, or {@code null} for a profile that states none
     */
    private static String profileNamespace(XmiIndex index, XmiProfile profile) {
        for (var metadataClass : metadataClassesOf(index, profile)) {
            for (var attribute : metadataClass.getAttributes()) {
                if (attribute.getName() != null
                        && attribute.getName().startsWith(VERSION_IRI_ATTRIBUTE)
                        && isUsable(attribute.getDefaultValue())) {
                    return asNamespace(attribute.getDefaultValue().strip());
                }
            }
        }
        return null;
    }

    private static String asNamespace(String versionIri) {
        if (versionIri.endsWith("#")) {
            return versionIri;
        }
        var versionSegment = versionIri.lastIndexOf('/');
        return versionSegment < 0
                ? versionIri + "#"
                : versionIri.substring(0, versionSegment) + "#";
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank();
    }

    /** The classes a profile states itself through, in the order the export declares them. */
    static List<XmiClass> metadataClassesOf(XmiIndex index, XmiProfile profile) {
        return profile.metadataClassIds().stream()
                .map(index.getClasses()::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
