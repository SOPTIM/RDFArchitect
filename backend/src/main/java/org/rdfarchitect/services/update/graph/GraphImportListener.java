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

import java.util.List;

/**
 * Receives the progress of an import of RDF files, and answers the one question such an import has
 * to ask. The namespace prefixes of an upload can collide with those of the dataset, and only this
 * kind of import can run into that, which is why the question sits here rather than on {@link
 * ImportProgressListener}: whoever takes a plain listener is never asked it.
 *
 * <p>All methods are optional; {@link #NOOP} ignores everything, never cancels and keeps the
 * prefixes of the dataset as they are.
 */
public interface GraphImportListener extends ImportProgressListener {

    GraphImportListener NOOP = new GraphImportListener() {};

    /**
     * The import is reading the namespace prefixes of the planned files to find out whether any of
     * them collide. Called once, after {@link #planned(List)} and before the first file is stored.
     */
    default void scanningPrefixes() {}

    /**
     * Asks what to do with the prefixes of the import once at least one of them is contested. The
     * import blocks on this call, so an implementation that lets a user decide may take as long as
     * it needs; it only has to return, or report itself {@link #isCancelled() cancelled}, for the
     * import to go on.
     *
     * @param comparison every prefix of the dataset and of the import, contested ones marked
     * @return what to do with each prefix; a binding without an answer keeps its default, which
     *     leaves the prefixes of the dataset as they are
     */
    default ResolvedPrefixes awaitResolvedPrefixes(List<PrefixComparison> comparison) {
        return ResolvedPrefixes.none();
    }
}
