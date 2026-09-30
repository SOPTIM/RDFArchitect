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

package org.rdfarchitect.services;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;

import java.util.UUID;

/**
 * Names for the descriptions the changelog shows.
 *
 * <p>An entry is read by the person who made the change, often after undoing it in a part of the
 * workspace they were not looking at. An identifier tells them nothing there, so everything that
 * has a name is named — and where there is none, the message says less rather than printing a UUID
 * that no reader can place.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChangeDescriptions {

    /**
     * Returns the name of a package, which lives in the schema: the layout stores its diagram under
     * the package's id but leaves the name empty until the package is renamed.
     *
     * @param schema the graph the package is declared in
     * @param diagramLayoutModel the layout, asked first in case the diagram carries a name
     * @param packageUUID the package
     * @return the name, or {@code ""} for a package that has none — the default diagram of a graph
     *     is not declared in the schema at all
     */
    public static String packageName(Graph schema, Model diagramLayoutModel, UUID packageUUID) {
        var fromLayout = diagramName(diagramLayoutModel, packageUUID);
        if (!fromLayout.isEmpty()) {
            return fromLayout;
        }
        try {
            return name(
                    CIMResourceUtils.findLabelForResource(
                            CIMResourceUtils.findResourceForUuid(schema, packageUUID)));
        } catch (IllegalStateException _) {
            return "";
        }
    }

    /**
     * Returns the name a diagram carries in the layout.
     *
     * @param diagramLayoutModel the layout the diagram lives in
     * @param diagramUUID the diagram
     * @return the name, or {@code ""} if it has none
     */
    public static String diagramName(Model diagramLayoutModel, UUID diagramUUID) {
        var diagram = DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID);
        return diagram == null ? "" : name(diagram.getName());
    }

    /**
     * Returns {@code candidate} if there is anything to show, otherwise {@code ""}.
     *
     * @param candidate a name, possibly {@code null} or blank
     * @return the name, or {@code ""}
     */
    public static String name(String candidate) {
        return candidate == null || candidate.isBlank() ? "" : candidate;
    }

    /**
     * Describes an action on something that may or may not have a name, without ever falling back
     * to an identifier.
     *
     * @param action what happened, e.g. {@code "Moved classes"}
     * @param what the kind of thing, e.g. {@code "diagram"}
     * @param name its name, possibly empty
     * @return {@code Moved classes in diagram "Overview"}, or {@code Moved classes} when unnamed
     */
    public static String in(String action, String what, String name) {
        return name.isEmpty() ? action : "%s in %s \"%s\"".formatted(action, what, name);
    }

    /**
     * Returns the local name of a class or package IRI — what the editor shows as its label.
     *
     * @param uri the IRI
     * @return the part after the last {@code #} or {@code /}
     */
    public static String localName(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        var cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut < 0 || cut == uri.length() - 1 ? uri : uri.substring(cut + 1);
    }
}
