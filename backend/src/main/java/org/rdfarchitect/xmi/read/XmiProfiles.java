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
import java.util.List;

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

    /** The profiles an already-read export holds, in the order the model declares them. */
    List<XmiProfile> of(XmiIndex index) {
        var profiles = new ArrayList<XmiProfile>();
        for (var cimClass : index.getClasses().values()) {
            var candidates = namespaceCandidatesOf(cimClass);
            if (candidates.isEmpty()) {
                continue;
            }
            var owner = index.getPackages().get(cimClass.getPackageId());
            if (owner != null) {
                profiles.add(new XmiProfile(owner.getId(), owner.getName(), candidates));
            }
        }
        return profiles;
    }

    private List<String> namespaceCandidatesOf(XmiClass cimClass) {
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
