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

package org.rdfarchitect.cim.data.dto.relations.uri;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class URITest {

    @Test
    void constructor_splitsAtHashSeparator() {
        var uri = new URI("http://example.com#Class");

        assertAll(
                  () -> assertThat(uri.getPrefix()).isEqualTo("http://example.com#"),
                  () -> assertThat(uri.getSuffix()).isEqualTo("Class"),
                  () -> assertThat(uri.toString()).isEqualTo("http://example.com#Class")
                 );
    }

    @Test
    void constructor_splitsAtSlashWhenNoHashExists() {
        var uri = new URI("http://example.com/path/Class");

        assertAll(
                  () -> assertThat(uri.getPrefix()).isEqualTo("http://example.com/path/"),
                  () -> assertThat(uri.getSuffix()).isEqualTo("Class"),
                  () -> assertThat(uri.toString()).isEqualTo("http://example.com/path/Class")
                 );
    }

    @Test
    void constructor_withoutSeparator_keepsOriginalString() {
        var uri = new URI("ClassOnly");

        assertAll(
                  () -> assertThat(uri.getPrefix()).isNull(),
                  () -> assertThat(uri.getSuffix()).isEqualTo("ClassOnly"),
                  () -> assertThat(uri.toString()).isEqualTo("ClassOnly")
                 );
    }
}
