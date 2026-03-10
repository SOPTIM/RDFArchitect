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

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
@Log4j2
public class AppVersionResolver {

    static final String DEFAULT_VERSION = "0.0.0-SNAPSHOT";
    private static final Pattern SEMVER_TAG_PATTERN = Pattern.compile("^v(\\d+\\.\\d+\\.\\d+)$");

    private final String configuredVersion;
    private final Supplier<Optional<String>> gitPropertiesVersionSupplier;
    private final Supplier<Optional<String>> gitCommandVersionSupplier;

    @Autowired
    public AppVersionResolver(@Value("${APP_VERSION:}") String configuredVersion) {
        this(
                configuredVersion,
                () -> resolveVersionFromGitProperties(loadGitProperties()),
                AppVersionResolver::resolveVersionFromGitCommands
        );
    }

    AppVersionResolver(String configuredVersion,
                       Supplier<Optional<String>> gitPropertiesVersionSupplier,
                       Supplier<Optional<String>> gitCommandVersionSupplier) {
        this.configuredVersion = configuredVersion;
        this.gitPropertiesVersionSupplier = gitPropertiesVersionSupplier;
        this.gitCommandVersionSupplier = gitCommandVersionSupplier;
    }

    public String resolveVersion() {
        var normalizedConfiguredVersion = normalize(configuredVersion);
        if (!normalizedConfiguredVersion.isEmpty()) {
            return normalizedConfiguredVersion;
        }

        return gitPropertiesVersionSupplier.get()
                                           .or(gitCommandVersionSupplier)
                                           .orElse(DEFAULT_VERSION);
    }

    static Optional<String> resolveVersionFromGitProperties(Properties gitProperties) {
        if (gitProperties.isEmpty()) {
            return Optional.empty();
        }

        var stableVersion = extractStableVersion(gitProperties.getProperty("git.closest.tag.name"));
        if (stableVersion.isEmpty()) {
            return Optional.empty();
        }

        return "0".equals(normalize(gitProperties.getProperty("git.closest.tag.commit.count")))
                ? stableVersion
                : stableVersion.map(version -> version + "-SNAPSHOT");
    }

    private static Properties loadGitProperties() {
        var properties = new Properties();
        try (var inputStream = AppVersionResolver.class.getClassLoader().getResourceAsStream("git.properties")) {
            if (inputStream == null) {
                return properties;
            }

            properties.load(inputStream);
        } catch (IOException exception) {
            log.debug("Unable to load git.properties for app version resolution", exception);
        }

        return properties;
    }

    private static Optional<String> resolveVersionFromGitCommands() {
        var exactVersion = readStableVersionFromGit("tag", "--points-at", "HEAD", "--sort=-version:refname");
        if (exactVersion.isPresent()) {
            return exactVersion;
        }

        return readStableVersionFromGit("tag", "--merged", "HEAD", "--sort=-version:refname")
                .map(version -> version + "-SNAPSHOT");
    }

    private static Optional<String> readStableVersionFromGit(String... args) {
        var gitCommand = new ArrayList<String>();
        gitCommand.add("git");
        gitCommand.addAll(List.of(args));

        var processBuilder = new ProcessBuilder(gitCommand);
        processBuilder.redirectErrorStream(true);

        try {
            var process = processBuilder.start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                return Optional.empty();
            }

            for (var line : output.split("\\R")) {
                var stableVersion = extractStableVersion(line);
                if (stableVersion.isPresent()) {
                    return stableVersion;
                }
            }
        } catch (IOException exception) {
            log.debug("Unable to execute git while resolving app version", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while resolving app version from git", exception);
        }

        return Optional.empty();
    }

    private static Optional<String> extractStableVersion(String tagName) {
        var normalizedTagName = normalize(tagName);
        if (normalizedTagName.isEmpty()) {
            return Optional.empty();
        }

        var matcher = SEMVER_TAG_PATTERN.matcher(normalizedTagName);
        return matcher.matches()
                ? Optional.of(matcher.group(1))
                : Optional.empty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
