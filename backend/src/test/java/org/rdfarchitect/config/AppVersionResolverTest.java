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

package org.rdfarchitect.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Properties;

class AppVersionResolverTest {

    @Test
    void resolveVersion_prefersConfiguredVersion() {
        var resolver = new AppVersionResolver("2.3.4", Optional::empty, () -> Optional.of("9.9.9"));

        assertThat(resolver.resolveVersion()).isEqualTo("2.3.4");
    }

    @Test
    void resolveVersion_usesGitPropertiesBeforeGitCommand() {
        var resolver =
                new AppVersionResolver(
                        "", () -> Optional.of("1.2.3-7-gabc12345"), () -> Optional.of("9.9.9"));

        assertThat(resolver.resolveVersion()).isEqualTo("1.2.3-7-gabc12345");
    }

    @Test
    void resolveVersion_fallsBackToDefaultWhenNoMetadataIsAvailable() {
        var resolver = new AppVersionResolver("", Optional::empty, Optional::empty);

        assertThat(resolver.resolveVersion()).isEqualTo(AppVersionResolver.DEFAULT_VERSION);
    }

    @Test
    void resolveVersionFromGitProperties_returnsReleaseVersionForExactTag() {
        var properties = new Properties();
        properties.setProperty("git.closest.tag.name", "v1.2.3");
        properties.setProperty("git.closest.tag.commit.count", "0");

        assertThat(AppVersionResolver.resolveVersionFromGitProperties(properties))
                .hasValue("1.2.3");
    }

    @Test
    void resolveVersionFromGitProperties_returnsDescribedVersionWhenAheadOfTag() {
        var properties = new Properties();
        properties.setProperty("git.closest.tag.name", "v1.2.3");
        properties.setProperty("git.closest.tag.commit.count", "7");
        properties.setProperty("git.commit.id.abbrev", "abc12345");

        assertThat(AppVersionResolver.resolveVersionFromGitProperties(properties))
                .hasValue("1.2.3-7-gabc12345");
    }

    @Test
    void resolveVersionFromGitProperties_ignoresNonSemverTags() {
        var properties = new Properties();
        properties.setProperty("git.closest.tag.name", "test");
        properties.setProperty("git.closest.tag.commit.count", "0");

        assertThat(AppVersionResolver.resolveVersionFromGitProperties(properties)).isEmpty();
    }
}
