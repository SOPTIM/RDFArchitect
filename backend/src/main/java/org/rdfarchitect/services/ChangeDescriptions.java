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
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
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
        if (!fromLayout.isEmpty() || schema == null) {
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
     * Returns the name of a class as the editor labels it.
     *
     * @param schema the graph the class is declared in
     * @param classUUID the class
     * @return the label, or {@code ""} for a class the graph does not declare — a change that
     *     removes one is described after the fact, when there is nothing left to look up
     */
    public static String className(Graph schema, UUID classUUID) {
        if (schema == null) {
            return "";
        }
        try {
            return name(
                    CIMResourceUtils.findLabelForResource(
                            CIMResourceUtils.findResourceForUuid(schema, classUUID)));
        } catch (IllegalStateException _) {
            return "";
        }
    }

    /**
     * Returns the name of a custom diagram, which carries its own: the DL model only stores an
     * empty one for them.
     *
     * @param diagram the diagram, possibly {@code null}
     * @return the name, or {@code ""} if it has none
     */
    public static String diagramName(CustomDiagram diagram) {
        return diagram == null ? "" : name(diagram.getName());
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
     * Returns the text of a label. A description must never print the label object itself, which is
     * what an unguarded {@code "%s"} does with it.
     *
     * @param label the label, possibly {@code null}
     * @return the text, or {@code ""} for a resource that carries none
     */
    public static String name(RDFSLabel label) {
        return label == null ? "" : name(label.getValue());
    }

    /**
     * Describes an action on a thing that may or may not have a name, without ever falling back to
     * an identifier.
     *
     * @param action what happened to it, e.g. {@code "Deleted"} or {@code "Added classes to"}
     * @param targetKind the kind of thing it happened to, e.g. {@code "diagram"}
     * @param targetName its name, possibly empty
     * @return {@code Deleted diagram "Overview"}, or {@code Deleted a diagram} when unnamed
     */
    public static String actionOn(String action, String targetKind, String targetName) {
        return targetName.isEmpty()
                ? "%s a %s".formatted(action, targetKind)
                : "%s %s \"%s\"".formatted(action, targetKind, targetName);
    }

    /**
     * Describes an action inside a thing that may or may not have a name. Where {@link
     * #actionOn(String, String, String)} names what was changed, this names where the change
     * happened — and says nothing about the place rather than naming it by identifier.
     *
     * @param action what happened, e.g. {@code "Moved classes"}
     * @param containerKind the kind of thing it happened in, e.g. {@code "package"}
     * @param containerName its name, possibly empty
     * @return {@code Moved classes in package "Core"}, or {@code Moved classes} when unnamed
     */
    public static String actionIn(String action, String containerKind, String containerName) {
        return containerName.isEmpty()
                ? action
                : "%s in %s \"%s\"".formatted(action, containerKind, containerName);
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
