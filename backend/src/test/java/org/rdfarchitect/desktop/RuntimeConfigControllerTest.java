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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

class RuntimeConfigControllerTest {

    @Test
    void writesTheConfigurationTheUserInterfaceReads() {
        var config = new LinkedHashMap<String, String>();
        config.put("PUBLIC_BACKEND_URL", "");
        config.put("PUBLIC_DESKTOP", "true");

        assertThat(RuntimeConfigController.render(config))
                .isEqualTo(
                        """
                        window.__RDFARCHITECT_CONFIG__ = {
                            PUBLIC_BACKEND_URL: "",
                            PUBLIC_DESKTOP: "true",
                        };
                        """);
    }

    @Test
    void quotesEveryValueAsAStringLiteral() {
        assertThat(RuntimeConfigController.jsString("1.2.3")).isEqualTo("\"1.2.3\"");
        assertThat(RuntimeConfigController.jsString("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(RuntimeConfigController.jsString("</script>\n"))
                .isEqualTo("\"\\u003c/script>\\u000a\"");
        assertThat(RuntimeConfigController.jsString(null)).isEqualTo("\"\"");
    }
}
