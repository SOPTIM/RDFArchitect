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

import java.util.regex.Pattern;

/** A minor version only adds what older importers can ignore; a major version breaks them. */
public record FormatVersion(int major, int minor) {

    private static final Pattern SYNTAX = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})");

    public static final FormatVersion CURRENT = parse(WorkspaceArchiveFormat.FORMAT_VERSION);

    public static FormatVersion parse(String version) {
        var matcher = SYNTAX.matcher(version == null ? "" : version.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "\"" + version + "\" is not a version of the form MAJOR.MINOR");
        }
        return new FormatVersion(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
