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

/**
 * What the import is to do with one namespace binding of a {@link PrefixComparison}. A binding is
 * addressed by its prefix and namespace URI together, because a prefix can be claimed by several
 * namespaces at once. A binding of the dataset is addressed the same way, which is what allows an
 * import to move an existing prefix out of the way.
 *
 * @param prefix the prefix in question, with or without a trailing colon
 * @param iri the namespace URI this decision is about
 * @param action what to do with the binding
 * @param newPrefix the prefix to bind the namespace to, only read for {@link Action#RENAME}
 */
public record PrefixResolution(String prefix, String iri, Action action, String newPrefix) {

    /** What is to become of a namespace binding. */
    public enum Action {
        /** The namespace keeps the prefix; at most one binding per prefix can. */
        KEEP,
        /** The namespace is bound to {@link PrefixResolution#newPrefix()} instead. */
        RENAME,
        /** The namespace stays, but without a prefix. */
        DROP
    }
}
