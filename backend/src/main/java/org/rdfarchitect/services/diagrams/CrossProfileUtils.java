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

package org.rdfarchitect.services.diagrams;

import lombok.experimental.UtilityClass;

import java.awt.Color;
import java.util.Random;

@UtilityClass
public class CrossProfileUtils {

    private static final Random random = new Random();

    public static String generateRandomDarkColor() {
        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.3f + random.nextFloat() * 0.4f;

        Color color = Color.getHSBColor(hue, saturation, brightness);
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
