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

package org.rdfarchitect.services.archive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

class GraphFolderNamesTest {

    @ParameterizedTest
    @CsvSource({
        "http://iec.ch/TC57/ns/CIM/Topology-EU/3.0, iec.ch-TC57-ns-CIM-Topology-EU-3.0",
        "http://graph#Header_AP, graph-Header_AP",
        "urn:uuid:1234, uuid-1234",
        "default, default",
        "http://example.org/a b/, example.org-a-b",
    })
    void folderNameOf_keepsWhatIsReadableAndReplacesTheRest(String graphUri, String folder) {
        assertThat(GraphFolderNames.folderNameOf(graphUri)).isEqualTo(folder);
    }

    @Test
    void folderNameOf_keepsTheEndOfALongUri_whereUrisOfOnePublisherDiffer() {
        var graphUri = "http://example.org/" + "x".repeat(100) + "/Equipment";

        var folder = GraphFolderNames.folderNameOf(graphUri);

        assertThat(folder).hasSize(GraphFolderNames.MAX_LENGTH).endsWith("-Equipment");
    }

    @Test
    void folderNameOf_neverStartsOrEndsWithADotOrDash() {
        assertThat(GraphFolderNames.folderNameOf("http://example.org/.hidden./"))
                .isEqualTo("example.org-.hidden");
    }

    @ParameterizedTest
    @CsvSource({"http://, graph", "http://con, graph-con", "http://NUL.txt, graph-NUL.txt"})
    void folderNameOf_avoidsNamesAFileSystemRejects(String graphUri, String folder) {
        assertThat(GraphFolderNames.folderNameOf(graphUri)).isEqualTo(folder);
    }

    @Test
    void assign_numbersFoldersThatWouldClash_evenWhenTheyOnlyDifferInCase() {
        var folders =
                GraphFolderNames.assign(
                        List.of(
                                "http://example.org/Core",
                                "http://example.org/core",
                                "http://example.org#Core"));

        assertThat(folders.values())
                .containsExactly("example.org-Core", "example.org-core-2", "example.org-Core-3");
    }

    @Test
    void assign_keepsANumberedFolderWithinTheMaximumLength() {
        var graphUri = "http://example.org/" + "x".repeat(100);

        var folders = GraphFolderNames.assign(List.of(graphUri, graphUri + "#"));

        assertThat(folders.get(graphUri + "#")).hasSize(GraphFolderNames.MAX_LENGTH).endsWith("-2");
    }
}
