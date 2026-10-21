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

import org.rdfarchitect.xmi.read.XmiPackageScope;

/**
 * One package of a read export that is to become a schema.
 *
 * <p>The choice is taken as given. Choosing a package together with one it contains writes the
 * inner one's classes into both schemas under the same uris, which is a thing two profiles already
 * do with the datatypes they share; keeping the caller from asking for it is the dialog's to do,
 * not this one's.
 *
 * @param fileIndex which of the read exports it was found in, by its position in the progress. The
 *     file name is not enough: two uploads may carry the same name
 * @param packageId the package's {@code xmi:id}, as the read reported it
 * @param name what the package is called, as the read reported it. Echoed back rather than looked
 *     up so that the progress can name it before its export has been read
 * @param scope whether the schema is the package with its sub-packages or the package alone, {@code
 *     null} counting as the former
 * @param graphUri what the schema is to be called, or {@code null} to name it after the package. A
 *     package declaring a profile commonly offers several candidates and only the caller can say
 *     which one is meant
 * @param extensionNamespace what the extension-stereotyped elements are named with, for an export
 *     that states it nowhere. Ignored where the export does state it
 */
public record SelectedPackageDTO(
        int fileIndex,
        String packageId,
        String name,
        XmiPackageScope scope,
        String graphUri,
        String extensionNamespace) {

    /** The scope as it was asked for, defaulting to the package with its sub-packages. */
    public XmiPackageScope scopeOrDefault() {
        return scope == null ? XmiPackageScope.WITH_SUB_PACKAGES : scope;
    }
}
