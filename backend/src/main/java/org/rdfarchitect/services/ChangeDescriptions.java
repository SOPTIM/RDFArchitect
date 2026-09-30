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

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;

import java.util.UUID;

/**
 * Names for the descriptions the changelog shows.
 *
 * <p>An entry is read by the person who made the change, often after undoing it in a part of the
 * workspace they were not looking at. An identifier tells them nothing there, so everything that
 * has a name is named, and only what has none falls back to its id.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChangeDescriptions {

    /**
     * Returns the name of a diagram as the user knows it.
     *
     * @param diagramLayoutModel the layout the diagram lives in
     * @param diagramUUID the diagram
     * @return the diagram's name, or its id if it has none
     */
    public static String diagram(Model diagramLayoutModel, UUID diagramUUID) {
        var diagram = DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID);
        return diagram == null
                ? String.valueOf(diagramUUID)
                : nameOr(diagram.getName(), diagramUUID);
    }

    /**
     * Returns {@code name}, or the identifier when there is no name to show.
     *
     * @param name the name, possibly {@code null} or blank
     * @param id what to fall back to
     * @return something the reader can recognise
     */
    public static String nameOr(String name, UUID id) {
        return name == null || name.isBlank() ? String.valueOf(id) : name;
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
