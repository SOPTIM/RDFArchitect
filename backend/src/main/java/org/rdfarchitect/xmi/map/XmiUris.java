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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.xmi.read.XmiClass;
import org.rdfarchitect.xmi.read.XmiNaming;
import org.rdfarchitect.xmi.read.XmiPackage;

import java.util.Set;

/**
 * The URI every resource of a profile gets.
 *
 * <p>Which namespace a resource lands in is decided by its stereotypes, not by where it sits: a
 * class stereotyped {@code Entsoe} or {@code European} belongs to the extension namespace even
 * though it is declared in the same package as its neighbours. Everything else takes the model
 * namespace, and only the package itself takes the profile's own — {@code <profile
 * ns>Package_<Name>}, which is the shape a release writes.
 *
 * <p>CGMES 2.4.15 has no profile namespace, so its package resource falls back to the model one.
 * That is what its release does too, rather than a gap being filled.
 */
@RequiredArgsConstructor
class XmiUris {

    private static final String PACKAGE_PREFIX = "Package_";

    private final XmiNamespaces namespaces;

    /** The URI of a class or an enumeration. */
    String of(XmiClass cimClass) {
        return namespaceFor(cimClass) + cimClass.getName();
    }

    /**
     * The URI of an attribute or an enum entry: qualified by the class owning it, but namespaced by
     * its own stereotypes rather than its owner's. A release does separate the two — {@code
     * ConnectivityNode} is a core class in the model namespace while {@code
     * ConnectivityNode.fromEndIsoCode} is an extension in the extension namespace.
     *
     * <p>Either of the two makes it one. An extension member of a core class follows itself, and a
     * plain member of an extension class follows its owner — every {@code <Profile>Version} of
     * CGMES 2.4.15 is an extension class whose ten attributes carry no stereotype of their own and
     * are named in the extension namespace all the same.
     */
    String of(XmiClass owner, String memberName, Set<String> memberStereotypes) {
        return memberNamespace(owner, memberStereotypes) + owner.getName() + "." + memberName;
    }

    /**
     * The URI of an association half, qualified by the class it is an attribute of — the class at
     * the <em>other</em> end from the one naming the role — and namespaced by its own stereotypes.
     */
    String ofAssociationHalf(XmiClass domain, String roleName, Set<String> stereotypes) {
        return memberNamespace(domain, stereotypes) + domain.getName() + "." + roleName;
    }

    /**
     * The URI of a classifier named but not declared in the model section: one of Enterprise
     * Architect's own datatypes, or one a partial export leaves out and stubs. Neither carries a
     * stereotype, so the model namespace is the only one it can take, and it is the one a class of
     * that name would take anyway.
     */
    String ofUndeclared(String name) {
        return namespaces.getModel() + name;
    }

    /**
     * Whether a package can be given a URI at all. Its name goes into the resource verbatim, and
     * CGMES 3.0 has one named {@code GeoSPARQL - Profile}.
     */
    boolean canName(XmiPackage cimPackage) {
        return cimPackage != null && XmiNaming.usableOrNull(cimPackage.getName()) != null;
    }

    /** The URI of the package resource standing for the profile. */
    String of(XmiPackage profilePackage) {
        var namespace =
                namespaces.getProfile() != null ? namespaces.getProfile() : namespaces.getModel();
        return namespace + PACKAGE_PREFIX + profilePackage.getName();
    }

    /**
     * Whether this class can be given a URI at all. One stereotyped as an extension cannot when the
     * export states no extension namespace, which a CGMES 2.4.15 profiles-only export does not.
     */
    boolean canName(XmiClass cimClass) {
        return !XmiStereotypes.isExtension(cimClass.getStereotypes()) || namespaces.hasExtension();
    }

    private String namespaceFor(XmiClass cimClass) {
        return namespaceFor(cimClass.getStereotypes());
    }

    private String namespaceFor(Set<String> stereotypes) {
        return XmiStereotypes.isExtension(stereotypes) && namespaces.hasExtension()
                ? namespaces.getExtension()
                : namespaces.getModel();
    }

    /** Whether a member stereotyped like this can be given a URI; see {@link #canName}. */
    boolean canName(Set<String> stereotypes) {
        return !XmiStereotypes.isExtension(stereotypes) || namespaces.hasExtension();
    }

    /** A member is an extension when either it or the class owning it is one. */
    private String memberNamespace(XmiClass owner, Set<String> memberStereotypes) {
        var isExtension =
                XmiStereotypes.isExtension(memberStereotypes)
                        || XmiStereotypes.isExtension(owner.getStereotypes());
        return isExtension && namespaces.hasExtension()
                ? namespaces.getExtension()
                : namespaces.getModel();
    }
}
