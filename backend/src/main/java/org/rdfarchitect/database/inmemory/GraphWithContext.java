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

package org.rdfarchitect.database.inmemory;

import lombok.Getter;
import lombok.Setter;

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.awt.Color;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper class that combines an {@link GraphRewindableWithUUIDs} with its associated {@link
 * DiagramLayout}.
 */
public class GraphWithContext {

    @Getter private final GraphRewindableWithUUIDs rdfGraph;

    @Getter
    private final ConcurrentHashMap<UUID, CustomDiagram> customDiagrams = new ConcurrentHashMap<>();

    @Getter @Setter private DiagramLayout diagramLayout = new DiagramLayout();

    @Getter @Setter private Model customSHACL;

    @Getter @Setter private String crossProfileDiagramColor;

    public GraphWithContext(GraphRewindableWithUUIDs rdfGraph) {
        this.rdfGraph = rdfGraph;
        this.crossProfileDiagramColor = generateRandomDarkColor();
    }

    private static String generateRandomDarkColor() {
        Random random = new Random();

        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.3f + random.nextFloat() * 0.4f;

        Color color = Color.getHSBColor(hue, saturation, brightness);
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
