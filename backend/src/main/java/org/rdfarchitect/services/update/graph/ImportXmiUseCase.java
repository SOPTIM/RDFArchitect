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

package org.rdfarchitect.services.update.graph;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Imports schemas from an Enterprise Architect XMI export. */
public interface ImportXmiUseCase {

    /**
     * A profile one of the uploaded exports holds.
     *
     * @param fileName the export the profile was found in
     * @param id the profile package's {@code xmi:id}, which addresses it for a later import
     * @param name the profile package's name
     * @param namespaceCandidates the profile URIs the package's metadata class declares; a released
     *     profile commonly declares several
     */
    record ScannedProfile(
            String fileName, String id, String name, List<String> namespaceCandidates) {}

    /**
     * Reads every uploaded export and reports the profiles it holds. Nothing is stored: which of
     * the profiles become schemas is a later decision.
     *
     * @param files the uploaded exports
     * @param listener receives the progress of the scan and can cancel it
     * @return the profiles found, in the order the exports declare them
     */
    List<ScannedProfile> scanProfiles(List<MultipartFile> files, ImportProgressListener listener);
}
