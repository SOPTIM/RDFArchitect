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

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which packages of an export hold a profile.
 *
 * <p>A package is not flagged as a profile by a stereotype or a tagged value; its content is the
 * marker. A package holds a profile exactly when it <em>directly</em> contains a metadata class,
 * that is a class with at least one attribute named like a profile URI and valued with an absolute
 * IRI. Matching the shape of the data rather than a known model is deliberate: the conventions
 * differ per release, and a vendor model will use another one.
 */
@UtilityClass
class XmiProfiles {

    /**
     * The attribute names a metadata class states its profile URI under. Matched as prefixes,
     * because {@code EquipmentVersion} spells them {@code baseURIcore}, {@code baseURIoperation}
     * and {@code baseURIshortCircuit}. Whether the attribute is static or read-only decides
     * nothing: not every metadata class says so.
     */
    private final List<String> PROFILE_URI_PREFIXES =
            List.of(
                    "versionIRI",
                    "baseURI",
                    "entsoeURI",
                    "europeanProfileURI",
                    "uri",
                    "namespaceURI");

    /** Whether an attribute of that name states a profile URI. */
    boolean statesProfileUri(String attributeName) {
        return attributeName != null
                && PROFILE_URI_PREFIXES.stream().anyMatch(attributeName::startsWith);
    }

    /**
     * The value as a profile URI, or {@code null} when it is not one. The leading space one
     * released profile puts in front of its URI is stripped here, because the value is a URI
     * everywhere downstream.
     */
    String profileUriOrNull(String value) {
        if (value == null) {
            return null;
        }
        var candidate = value.strip();
        try {
            return new URI(candidate).isAbsolute() ? candidate : null;
        } catch (URISyntaxException _) {
            return null;
        }
    }

    /**
     * The profiles an already-read export holds, in the order the model declares them.
     *
     * <p>A package is reported once however many metadata classes it holds, and their candidates
     * are merged: the CGMES 2.4.15 full model has packages where two of them meet, and a profile
     * offered twice would be imported twice into two graphs of the same content.
     */
    List<XmiProfile> of(XmiIndex index) {
        var candidatesByPackage = new LinkedHashMap<String, Set<String>>();
        var metadataClassesByPackage = new LinkedHashMap<String, Set<String>>();
        for (var cimClass : index.getClasses().values()) {
            var candidates = namespaceCandidatesOf(cimClass);
            if (candidates.isEmpty()) {
                continue;
            }
            var owner = index.getPackages().get(cimClass.getPackageId());
            if (owner != null) {
                candidatesByPackage
                        .computeIfAbsent(owner.getId(), _ -> new LinkedHashSet<>())
                        .addAll(candidates);
                metadataClassesByPackage
                        .computeIfAbsent(owner.getId(), _ -> new LinkedHashSet<>())
                        .add(cimClass.getId());
            }
        }
        return candidatesByPackage.entrySet().stream()
                .map(
                        found ->
                                new XmiProfile(
                                        found.getKey(),
                                        index.getPackages().get(found.getKey()).getName(),
                                        List.copyOf(metadataClassesByPackage.get(found.getKey())),
                                        List.copyOf(found.getValue())))
                .toList();
    }

    /**
     * An enumeration is never a metadata class, and leaving it in would make this pass disagree
     * with the scan, which only ever looks at a {@code uml:Class}. The one decides what is offered
     * and the other what is imported, so the two have to select the same packages.
     */
    private List<String> namespaceCandidatesOf(XmiClass cimClass) {
        if (cimClass.isEnumeration()) {
            return List.of();
        }
        var candidates = new ArrayList<String>();
        for (var attribute : cimClass.getAttributes()) {
            if (!statesProfileUri(attribute.getName())) {
                continue;
            }
            var uri = profileUriOrNull(attribute.getDefaultValue());
            if (uri != null) {
                candidates.add(uri);
            }
        }
        return List.copyOf(candidates);
    }
}
