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

package org.rdfarchitect.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ClassUMLAdaptedDTODeserializationTest {

    // Spring Boot enables this feature, so a primitive field would reject a null value
    private final ObjectMapper objectMapper =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    @Test
    void deserialize_externalIsNull_readsClass() {
        var json =
                """
                {
                  "uuid": "43836908-c7f7-4749-bb8b-3ac9250de655",
                  "prefix": "http://example.org#",
                  "label": "class",
                  "external": null,
                  "superClass": {
                    "uuid": "93ee2f31-5ddd-4b25-b119-e90a5ed327b0",
                    "prefix": "http://example.org#",
                    "label": "superClass",
                    "external": null
                  }
                }
                """;

        assertThatCode(
                        () -> {
                            var dto = objectMapper.readValue(json, ClassUMLAdaptedDTO.class);
                            assertThat(dto.getExternal()).isNull();
                            assertThat(dto.getSuperClass().getExternal()).isNull();
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void deserialize_externalIsMissing_readsClass() throws Exception {
        var json =
                """
                {
                  "uuid": "43836908-c7f7-4749-bb8b-3ac9250de655",
                  "prefix": "http://example.org#",
                  "label": "class"
                }
                """;

        var dto = objectMapper.readValue(json, ClassUMLAdaptedDTO.class);

        assertThat(dto.getExternal()).isNull();
        assertThat(dto.getLabel()).isEqualTo("class");
    }
}
