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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;

class LaunchPagesTest {

    private static final DesktopAccessToken TOKEN = new DesktopAccessToken("t0k3n_-");

    @TempDir Path directory;

    private LaunchPages pages() {
        return new LaunchPages(directory, 17407, TOKEN);
    }

    @Test
    void sessionUrl_pageWithQuery_namesTokenAndEncodedPage() {
        assertThat(pages().sessionUrl("/mainpage?open=a,b"))
                .isEqualTo(
                        "http://127.0.0.1:17407/desktop/session?token=t0k3n_-"
                                + "&next=%2Fmainpage%3Fopen%3Da%2Cb");
    }

    @Test
    void appWindowPage_anyPage_isOneFileWithThePageInTheFragment() throws Exception {
        var first = pages().appWindowPage("/mainpage?open=a");
        var second = pages().appWindowPage("/mainpage");

        var page = directory.resolve("launch.html");
        assertThat(first.getScheme()).isEqualTo("file");
        assertThat(Path.of(first.getPath())).isEqualTo(page);
        assertThat(first.getRawFragment()).isEqualTo("%2Fmainpage%3Fopen%3Da");
        assertThat(Path.of(second.getPath())).isEqualTo(page);
        assertThat(second.getRawFragment()).isEqualTo("%2Fmainpage");
        assertThat(Files.readString(page))
                .contains("location.hash")
                .contains(
                        "location.replace(\"http://127.0.0.1:17407/desktop/session?token=t0k3n_-"
                                + "&next=\" + encodeURIComponent(next))")
                .contains(
                        "<noscript><meta http-equiv=\"refresh\" content=\"0;"
                                + " url=http://127.0.0.1:17407/desktop/session?token=t0k3n_-"
                                + "&amp;next=%2Fmainpage\"></noscript>");
        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(page);
        }
    }

    @Test
    void appWindowPage_anotherRun_replacesThePage() throws Exception {
        pages().appWindowPage("/mainpage");

        new LaunchPages(directory, 18000, new DesktopAccessToken("next"))
                .appWindowPage("/mainpage");

        assertThat(Files.readString(directory.resolve("launch.html")))
                .contains("127.0.0.1:18000/desktop/session?token=next&")
                .doesNotContain("t0k3n_-");
    }

    @Test
    void in_otherDirectory_writesThePagesThere(@TempDir Path other) throws Exception {
        var page = pages().in(other).appWindowPage("/mainpage");

        assertThat(Path.of(page.getPath())).isEqualTo(other.resolve("launch.html"));
        assertThat(directory.resolve("launch.html")).doesNotExist();
    }

    @Test
    void windowPage_eachWindow_writesAPageOfItsOwnWithThePageInside() throws Exception {
        var first = pages().windowPage("/mainpage?open=a");
        var second = pages().windowPage("/mainpage?open=b");

        assertThat(first).isNotEqualTo(second);
        assertThat(first.getFragment()).isNull();
        assertThat(Files.readString(Path.of(first)))
                .contains(
                        "location.replace(\"http://127.0.0.1:17407/desktop/session?token=t0k3n_-"
                                + "&next=%2Fmainpage%3Fopen%3Da\")")
                .contains("url=http://127.0.0.1:17407/desktop/session?token=t0k3n_-&amp;next=");
        assertThat(Files.readString(Path.of(second))).contains("%3Fopen%3Db");
    }

    @Test
    void windowPage_pagesOfEarlierWindows_deletesTheOldOnes() throws Exception {
        var old = Path.of(pages().windowPage("/mainpage"));
        var recent = Path.of(pages().windowPage("/mainpage"));
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
        var other = Files.writeString(directory.resolve("instance.json"), "{}");
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().minus(Duration.ofHours(1))));

        pages().windowPage("/mainpage");

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(other).exists();
    }

    @Test
    void appWindowPageAndWindowPage_posixFileSystem_areReadableByTheOwnerOnly() throws Exception {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));

        var appWindowPage = directory.resolve("launch.html");
        pages().appWindowPage("/mainpage");
        var windowPage = Path.of(pages().windowPage("/mainpage"));

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(appWindowPage)))
                .isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(windowPage)))
                .isEqualTo("rw-------");
    }
}
