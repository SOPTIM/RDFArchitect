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

package org.rdfarchitect.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class OpenFileEventsTest {

    @AfterEach
    void tearDown() {
        OpenFileEvents.reset();
    }

    @Test
    void keepsFilesUntilTheAppIsReadyAndHandsOnLaterOnes() {
        OpenFileEvents.receive(List.of(Path.of("a.rdfa")));
        var later = new ArrayList<Path>();

        var early = OpenFileEvents.takeAndForward(later::addAll);
        OpenFileEvents.receive(List.of(Path.of("b.rdfa")));

        assertThat(early).containsExactly(Path.of("a.rdfa"));
        assertThat(later).containsExactly(Path.of("b.rdfa"));
    }
}
