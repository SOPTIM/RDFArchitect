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

package org.rdfarchitect.rdf;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RDFUtilsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:1",
                "https://localhost:1",
                "http://www.google.de",
            })
    void wrapURLorLiteral_stringIsAnURL_returnsStringWrappedAsURI(String candidate) {
        assertThat(RDFUtils.wrapURLorLiteral(candidate)).isEqualTo("<" + candidate + ">");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "!\"²§%&/{([)]=}?\\`´*+~'#",
                "foo://",
                "http://foo  foo",
                " ",
                "localhost",
                "foo#foo#foo",
                "foo.foo.foo",
                "foo.foo.foo.foo.foo.foo.foo.foo",
                "http://",
                "https://"
            })
    void wrapURLorLiteral_stringIsNotAnURL_returnsStringWrappedAsLiteral(String candidate) {
        assertThat(RDFUtils.wrapURLorLiteral(candidate)).isEqualTo("\"" + candidate + "\"");
    }

    @Test
    void wrapURLorLiteral_null_throwsNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> RDFUtils.wrapURLorLiteral(null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:1",
                "https://localhost:1",
                "http://www.google.de",
            })
    void isURI_stringIsAnURL_returnsTrue(String candidate) {
        assertThat(RDFUtils.isURL(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "!\"²§%&/{([)]=}?\\`´*+~'#",
                "foo://",
                "http://foo  foo",
                " ",
                "localhost",
                "foo#foo#foo",
                "foo.foo.foo",
                "foo.foo.foo.foo.foo.foo.foo.foo",
                "http://",
                "https://"
            })
    void isURI_stringIsNotAnURL_returnsFalse(String candidate) {
        assertThat(RDFUtils.isURL(candidate)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isURL_nullAndEmpty_returnsFalse(String candidate) {
        assertThat(RDFUtils.isURL(candidate)).isFalse();
    }
}
