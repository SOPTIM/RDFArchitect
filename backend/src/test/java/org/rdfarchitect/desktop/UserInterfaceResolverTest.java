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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserInterfaceResolverTest {

    @ParameterizedTest
    @CsvSource({
        "mainpage, true",
        "changelog, true",
        "shacl/workbench, true",
        "'', true",
        "favicon.svg, false",
        "_app/immutable/entry/start.js, false",
        "_app/version.json, false",
        "api/workspaces, false",
        "api, false",
        "v3/api-docs, false",
    })
    void isRoute_resourcePath_returnsTrueOnlyForRoutesAnsweredWithIndex(
            String path, boolean route) {
        assertThat(UserInterfaceResolver.isRoute(path)).isEqualTo(route);
    }
}
