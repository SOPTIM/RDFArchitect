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

package org.rdfarchitect.rdf.graph;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeltaPrefixMappingTest {

    private static final String EX_URI = "http://example.org/ex#";
    private static final String FOO_URI = "http://example.org/foo#";
    private static final String BAR_URI = "http://example.org/bar#";

    private PrefixMapping base;

    @BeforeEach
    void setUp() {
        base = new PrefixMappingImpl();
        base.setNsPrefix("ex", EX_URI);
    }

    @Test
    void newMapping_withoutChanges_hasNoChanges() {
        var mapping = new DeltaPrefixMapping(base);

        assertThat(mapping.hasChanges()).isFalse();
    }

    @Test
    void newMapping_exposesBasePrefixes() {
        var mapping = new DeltaPrefixMapping(base);

        assertThat(mapping.getNsPrefixURI("ex")).isEqualTo(EX_URI);
        assertThat(mapping.getNsPrefixMap()).containsEntry("ex", EX_URI);
    }

    @Test
    void setNsPrefix_addsToAdditionsAndIsVisible() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.setNsPrefix("foo", FOO_URI);

        assertThat(mapping.hasChanges()).isTrue();
        assertThat(mapping.getAddedPrefixes()).containsEntry("foo", FOO_URI);
        assertThat(mapping.getNsPrefixURI("foo")).isEqualTo(FOO_URI);
        assertThat(mapping.getNsPrefixMap())
                .containsEntry("foo", FOO_URI)
                .containsEntry("ex", EX_URI);
    }

    @Test
    void setNsPrefix_identicalToBase_isNotAChange() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.setNsPrefix("ex", EX_URI);

        assertThat(mapping.hasChanges()).isFalse();
        assertThat(mapping.getAddedPrefixes()).doesNotContainKey("ex");
        assertThat(mapping.getNsPrefixURI("ex")).isEqualTo(EX_URI);
    }

    @Test
    void setNsPrefix_invalidPrefix_throws() {
        var mapping = new DeltaPrefixMapping(base);

        assertThatExceptionOfType(PrefixMapping.IllegalPrefixException.class)
                .isThrownBy(() -> mapping.setNsPrefix("1invalid", FOO_URI));
    }

    @Test
    void removeNsPrefix_basePrefix_recordsDeletionAndHidesPrefix() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.removeNsPrefix("ex");

        assertThat(mapping.hasChanges()).isTrue();
        assertThat(mapping.getDeletedPrefixes()).containsEntry("ex", EX_URI);
        assertThat(mapping.getNsPrefixURI("ex")).isNull();
        assertThat(mapping.getNsPrefixMap()).doesNotContainKey("ex");
    }

    @Test
    void removeNsPrefix_thenSetAgain_clearsDeletion() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.removeNsPrefix("ex");
        mapping.setNsPrefix("ex", BAR_URI);

        assertThat(mapping.getDeletedPrefixes()).doesNotContainKey("ex");
        assertThat(mapping.getNsPrefixURI("ex")).isEqualTo(BAR_URI);
    }

    @Test
    void setNsPrefix_overridingBaseValue_usesNewValue() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.setNsPrefix("ex", BAR_URI);

        assertThat(mapping.hasChanges()).isTrue();
        assertThat(mapping.getNsPrefixURI("ex")).isEqualTo(BAR_URI);
        assertThat(mapping.getNsPrefixMap()).containsEntry("ex", BAR_URI);
    }

    @Test
    void setThenRemoveAddedPrefix_isNotVisibleAndNoDeletionRecorded() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.setNsPrefix("foo", FOO_URI);
        mapping.removeNsPrefix("foo");

        assertThat(mapping.getAddedPrefixes()).doesNotContainKey("foo");
        // "foo" is not declared in the base, so removing it records no deletion
        // (mirrors DeltaCompressible#performDelete, which only records base triples).
        assertThat(mapping.getDeletedPrefixes()).doesNotContainKey("foo");
        assertThat(mapping.getNsPrefixURI("foo")).isNull();
    }

    @Test
    void clearNsPrefixMap_removesAllBasePrefixes() {
        var mapping = new DeltaPrefixMapping(base);

        mapping.clearNsPrefixMap();

        assertThat(mapping.getNsPrefixMap()).isEmpty();
        assertThat(mapping.getDeletedPrefixes()).containsEntry("ex", EX_URI);
        assertThat(mapping.hasChanges()).isTrue();
    }

    @Test
    void setNsPrefixes_fromPrefixMapping_addsAll() {
        var other = new PrefixMappingImpl();
        other.setNsPrefix("foo", FOO_URI);
        other.setNsPrefix("bar", BAR_URI);
        var mapping = new DeltaPrefixMapping(base);

        mapping.setNsPrefixes(other);

        assertThat(mapping.getNsPrefixMap())
                .containsEntry("foo", FOO_URI)
                .containsEntry("bar", BAR_URI)
                .containsEntry("ex", EX_URI);
    }

    @Test
    void expandPrefix_resolvesFoldedState() {
        var mapping = new DeltaPrefixMapping(base);
        mapping.setNsPrefix("foo", FOO_URI);

        assertThat(mapping.expandPrefix("foo:Thing")).isEqualTo(FOO_URI + "Thing");
        assertThat(mapping.expandPrefix("ex:Thing")).isEqualTo(EX_URI + "Thing");
    }

    @Test
    void shortForm_resolvesFoldedState() {
        var mapping = new DeltaPrefixMapping(base);
        mapping.setNsPrefix("foo", FOO_URI);

        assertThat(mapping.shortForm(FOO_URI + "Thing")).isEqualTo("foo:Thing");
    }

    @Test
    void samePrefixMappingAs_equalFoldedState_returnsTrue() {
        var mapping = new DeltaPrefixMapping(base);
        mapping.setNsPrefix("foo", FOO_URI);

        var expected = new PrefixMappingImpl();
        expected.setNsPrefix("ex", EX_URI);
        expected.setNsPrefix("foo", FOO_URI);

        assertThat(mapping.samePrefixMappingAs(expected)).isTrue();
    }

    @Test
    void numPrefixes_reflectsFoldedState() {
        var mapping = new DeltaPrefixMapping(base);
        mapping.setNsPrefix("foo", FOO_URI);
        mapping.removeNsPrefix("ex");

        assertThat(mapping.numPrefixes()).isEqualTo(1);
    }
}
