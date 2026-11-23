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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class WorkspaceArchiveFormatTest {

    static Stream<Arguments> workspaceNames() {
        return Stream.of(
                Arguments.of("CGMES 3.0", "CGMES 3.0.rdfa"),
                Arguments.of("team/project: draft?", "team_project_ draft_.rdfa"),
                Arguments.of("a\\b*c\"d<e>f|g\u0007", "a_b_c_d_e_f_g_.rdfa"),
                Arguments.of("   ", "workspace.rdfa"));
    }

    @ParameterizedTest
    @MethodSource("workspaceNames")
    void fileNameFor_replacesWhatAFileSystemRejects(String workspaceName, String fileName) {
        assertThat(WorkspaceArchiveFormat.fileNameFor(workspaceName)).isEqualTo(fileName);
    }
}
