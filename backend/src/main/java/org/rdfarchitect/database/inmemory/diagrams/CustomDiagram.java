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

package org.rdfarchitect.database.inmemory.diagrams;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A custom diagram: a named selection of classes, possibly drawn from several graphs.
 *
 * <p>Holds no transaction state of its own. Creating, changing and deleting a diagram are all
 * changes to the {@link CustomDiagramCollection} that holds it, which is the transaction
 * participant.
 */
public class CustomDiagram {

    @Getter private final UUID diagramId;
    @Getter @Setter private String name;
    private List<ClassInDiagram> classes;

    private static List<ClassInDiagram> deepCopyClasses(List<ClassInDiagram> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        var copy = new ArrayList<ClassInDiagram>(source.size());
        for (var c : source) {
            copy.add(new ClassInDiagram(c.getUuid(), c.getGraphUri()));
        }
        return copy;
    }

    /**
     * Jackson deserialization constructor. The {@code diagramId} field is {@code final} and must be
     * supplied via {@code @JsonCreator} since a no-args constructor cannot initialise it.
     */
    @JsonCreator
    public CustomDiagram(
            @JsonProperty("diagramId") UUID diagramId,
            @JsonProperty("name") String name,
            @JsonProperty("classes") List<ClassInDiagram> classes) {
        this.diagramId = diagramId;
        this.name = name;
        setClasses(classes);
    }

    public CustomDiagram(UUID diagramId) {
        this.diagramId = diagramId;
    }

    // -------------------------------------------------------------------------
    // Accessors for classes (defensive copying)
    // -------------------------------------------------------------------------

    /**
     * Returns a deep copy of the classes list so that callers cannot mutate internal state.
     *
     * @return a deep copy of the diagram's class list, never {@code null}
     */
    public List<ClassInDiagram> getClasses() {
        return deepCopyClasses(classes);
    }

    /**
     * Stores a deep copy of the provided list, preventing external aliasing after assignment.
     *
     * @param classes the new list of classes; {@code null} is treated as an empty list
     */
    public void setClasses(List<ClassInDiagram> classes) {
        this.classes = deepCopyClasses(classes);
    }

    /** Returns an independent copy, for snapshotting the collection this diagram belongs to. */
    public CustomDiagram copy() {
        return new CustomDiagram(diagramId, name, getClasses());
    }
}
