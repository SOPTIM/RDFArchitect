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

package org.rdfarchitect.xmi.map;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.xmi.read.XmiDiagram;
import org.rdfarchitect.xmi.read.XmiIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the class diagrams of an export become once its profiles have been written.
 *
 * <p>A diagram draws classes by {@code xmi:id} and the import mints a uuid per class, so a diagram
 * can only be placed after the profiles it draws from are in. That is what {@link ClassLocation} is
 * for: it says, per class of the export, which schema the class ended up in and under which uuid.
 *
 * <h2>Which diagrams are taken</h2>
 *
 * Only those every class of which was written. A diagram reaching into a profile that was not
 * chosen would otherwise arrive with holes in it — not visibly broken, just quietly missing the
 * classes that carry its meaning, which is worse than not having it at all.
 *
 * <p>A shape the export draws that is no class is not such a hole. An Enterprise Architect diagram
 * carries boundaries, notes and free text beside its classes, and the reader has already dropped
 * those from the index; they are passed over here rather than taken for a missing class.
 */
@UtilityClass
public class XmiDiagrams {

    /**
     * Where one class of the export ended up.
     *
     * @param uuid the uuid it was written under
     * @param classUri what it is called in its schema
     * @param graphUri the schema it was written into
     */
    public record ClassLocation(UUID uuid, String classUri, String graphUri) {}

    /**
     * One class as a diagram places it.
     *
     * @param x position as the export states it, in its own coordinates
     */
    public record PlacedClass(ClassLocation location, String name, float x, float y) {}

    /**
     * A diagram of the export, ready to be stored.
     *
     * @param classes in the z-order the export draws them, backmost first
     */
    public record ImportedDiagram(UUID id, String name, List<PlacedClass> classes) {}

    /**
     * The diagrams of the export that the written classes fully account for.
     *
     * @param index the export, already read
     * @param locations where each written class ended up, by its {@code xmi:id}
     */
    public List<ImportedDiagram> of(XmiIndex index, Map<String, ClassLocation> locations) {
        var imported = new ArrayList<ImportedDiagram>();
        for (var diagram : index.getDiagrams().values()) {
            importedFrom(index, diagram, locations).ifPresent(imported::add);
        }
        return imported;
    }

    private Optional<ImportedDiagram> importedFrom(
            XmiIndex index, XmiDiagram diagram, Map<String, ClassLocation> locations) {
        var drawn = new ArrayList<DrawnClass>();
        for (var shape : diagram.getShapes()) {
            var cimClass = index.getClasses().get(shape.getClassId());
            if (cimClass == null) {
                continue;
            }
            var location = locations.get(shape.getClassId());
            if (location == null) {
                return Optional.empty();
            }
            drawn.add(
                    new DrawnClass(
                            new PlacedClass(
                                    location, cimClass.getName(), shape.getX(), shape.getY()),
                            shape.getZPosition()));
        }
        if (drawn.isEmpty()) {
            return Optional.empty();
        }
        // Stored positions carry no z of their own, so the order the classes go in is what
        // reproduces the one the export draws them in.
        drawn.sort(Comparator.comparingInt(DrawnClass::z));
        return Optional.of(
                new ImportedDiagram(
                        UUID.randomUUID(),
                        nameOf(index, diagram),
                        drawn.stream().map(DrawnClass::placed).toList()));
    }

    /** The name the extension states, falling back to the package the diagram is declared in. */
    private String nameOf(XmiIndex index, XmiDiagram diagram) {
        if (diagram.getName() != null && !diagram.getName().isBlank()) {
            return diagram.getName();
        }
        var owner = index.getPackages().get(diagram.getOwningPackageId());
        return owner == null ? "Diagram" : owner.getName();
    }

    private record DrawnClass(PlacedClass placed, int z) {}
}
