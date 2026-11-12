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

package org.rdfarchitect.models.changelog;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * How much of a workspace a restore puts back.
 *
 * <p>Restricting it is what lets a user put one profile back without touching the others. An empty
 * set means no restriction, so {@link #everything()} covers the whole workspace.
 *
 * <p>Deliberately not divisible by kind of data within a graph. A restore that put the schema back
 * and left the layout where it is would leave it describing classes that no longer exist, and
 * nothing cleans that up.
 *
 * @param graphUris the graphs to put back, or empty for the whole workspace
 */
public record RevertScope(Set<String> graphUris) {

    public RevertScope {
        graphUris = Set.copyOf(graphUris);
    }

    /** Returns the scope that holds nothing back. */
    public static RevertScope everything() {
        return new RevertScope(Set.of());
    }

    /**
     * Returns the scope covering only the given graphs.
     *
     * @param graphUris the graphs to put back
     * @return the scope
     */
    public static RevertScope ofGraphs(String... graphUris) {
        return new RevertScope(Set.of(graphUris));
    }

    /**
     * Returns whether a version recorded against {@code id} is one this restore puts back.
     *
     * <p>A restore restricted to graphs leaves everything the workspace owns alone — the set of
     * graphs, the shared prefixes, the workspace's own diagrams, the shapes documents. Those are
     * not divisible by graph: putting the set of graphs back to restore one of them would take
     * every other graph created since with it, and a document can apply to several schemas or none.
     * Restoring a graph therefore means restoring its contents, and what became of the graph itself
     * is a workspace-level decision.
     *
     * @param id what the recorded version belongs to, as it stands now rather than as it was
     *     recorded — the name a change is shown under has to be the name it can be restored with
     * @return whether it is covered
     */
    public boolean covers(ParticipantId id) {
        return graphUris.isEmpty() || (id.isGraphScoped() && graphUris.contains(id.scope()));
    }

    /** Returns whether this scope holds nothing back. */
    public boolean isEverything() {
        return graphUris.isEmpty();
    }

    /**
     * Names what this scope covers the way the changelog names schemas elsewhere, for the message
     * of the commit that records the restore.
     *
     * @return {@code schema "EQ"}, {@code schemas "EQ", "SSH"}, or {@code the workspace}
     */
    public String describe() {
        if (isEverything()) {
            return "the workspace";
        }
        var names =
                graphUris.stream()
                        .map(RevertScope::localName)
                        .sorted()
                        .map("\"%s\""::formatted)
                        .collect(Collectors.joining(", "));
        return (graphUris.size() == 1 ? "schema " : "schemas ") + names;
    }

    /** The part of a graph URI after its last {@code #} or {@code /}, as the import names it. */
    private static String localName(String uri) {
        var cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut < 0 || cut == uri.length() - 1 ? uri : uri.substring(cut + 1);
    }
}
