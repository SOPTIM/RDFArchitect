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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class OpenRequestsTest {

    @TempDir Path directory;

    private final OpenRequests openRequests = new OpenRequests();

    @Test
    void add_workspaceFile_isRegisteredUntilRemoved() throws Exception {
        var file = Files.writeString(directory.resolve("Draft.RDFA"), "zip");

        var request = openRequests.add(file);

        assertThat(request.fileName()).isEqualTo("Draft.RDFA");
        assertThat(openRequests.get(request.id())).contains(request);
        assertThat(openRequests.remove(request.id())).contains(request);
        assertThat(openRequests.get(request.id())).isEmpty();
    }

    @Test
    void add_noReadableWorkspaceFile_isRefused() throws Exception {
        var text = Files.writeString(directory.resolve("notes.txt"), "text");

        assertThatThrownBy(() -> openRequests.add(text))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> openRequests.add(directory.resolve("missing.rdfa")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> openRequests.add(Files.createDirectory(directory.resolve("x.rdfa"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void add_linkNamedLikeAWorkspaceFileToAnotherFile_isRefused() throws Exception {
        var secret = Files.writeString(directory.resolve("id_rsa"), "secret");
        var link = createLinkOrSkip(directory.resolve("Draft.rdfa"), secret);

        assertThatThrownBy(() -> openRequests.add(link))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void add_linkToAWorkspaceFile_keepsThePathItLeadsTo() throws Exception {
        var file = Files.writeString(directory.resolve("Real.rdfa"), "zip");
        var link = createLinkOrSkip(directory.resolve("Link.rdfa"), file);

        var request = openRequests.add(link);

        assertThat(request.path()).isEqualTo(file.toRealPath());
        assertThat(request.fileName()).isEqualTo("Real.rdfa");
    }

    private static Path createLinkOrSkip(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            return Assumptions.abort("No symbolic links here: " + e);
        }
    }

    @Test
    void get_noOrUnknownId_isEmpty() {
        assertThat(openRequests.get(null)).isEmpty();
        assertThat(openRequests.remove("unknown")).isEmpty();
    }
}
