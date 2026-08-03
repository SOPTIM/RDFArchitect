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

package org.rdfarchitect.database.snapshots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.SnapshotPort;
import org.rdfarchitect.exception.database.DataAccessException;

class FallbackSnapshotAdapterTest {

    private SnapshotPort primary;
    private SnapshotPort fallback;
    private FallbackSnapshotAdapter adapter;

    @BeforeEach
    void setUp() {
        primary = mock(SnapshotPort.class);
        fallback = mock(SnapshotPort.class);
        adapter = new FallbackSnapshotAdapter(primary, fallback);
    }

    @Test
    void createSnapshot_primaryAvailable_usesPrimary() {
        when(primary.isAvailable()).thenReturn(true);
        when(primary.createSnapshot("ds")).thenReturn("token");

        assertEquals("token", adapter.createSnapshot("ds"));
        verifyNoInteractions(fallback);
    }

    @Test
    void createSnapshot_primaryUnavailable_usesFallback() {
        when(primary.isAvailable()).thenReturn(false);
        when(fallback.createSnapshot("ds")).thenReturn("token");

        assertEquals("token", adapter.createSnapshot("ds"));
        verify(primary, never()).createSnapshot(any());
    }

    @Test
    void createSnapshot_primaryAvailableButRejects_usesFallback() {
        when(primary.isAvailable()).thenReturn(true);
        when(primary.createSnapshot("ds")).thenThrow(new DataAccessException());
        when(fallback.createSnapshot("ds")).thenReturn("token");

        assertEquals("token", adapter.createSnapshot("ds"));
    }

    @Test
    void snapshotExists_primaryAvailableButRejects_reportsNotFound() {
        when(fallback.snapshotExists("token")).thenReturn(false);
        when(primary.isAvailable()).thenReturn(true);
        when(primary.snapshotExists("token")).thenThrow(new DataAccessException());

        assertFalse(adapter.snapshotExists("token"));
    }

    @Test
    void fetchSnapshot_tokenInFallback_usesFallback() {
        when(fallback.snapshotExists("token")).thenReturn(true);

        adapter.fetchSnapshot("token");

        verify(fallback).fetchSnapshot("token");
        verify(primary, never()).fetchSnapshot(any());
    }

    @Test
    void fetchSnapshot_tokenNotInFallback_usesPrimary() {
        when(fallback.snapshotExists("token")).thenReturn(false);

        adapter.fetchSnapshot("token");

        verify(primary).fetchSnapshot("token");
        verify(fallback, never()).fetchSnapshot(any());
    }

    @Test
    void snapshotExists_checksFallbackFirstThenPrimary() {
        when(fallback.snapshotExists("token")).thenReturn(false);
        when(primary.isAvailable()).thenReturn(true);
        when(primary.snapshotExists("token")).thenReturn(true);

        assertTrue(adapter.snapshotExists("token"));
    }

    @Test
    void snapshotExists_primaryUnavailable_onlyChecksFallback() {
        when(fallback.snapshotExists("token")).thenReturn(false);
        when(primary.isAvailable()).thenReturn(false);

        assertFalse(adapter.snapshotExists("token"));
        verify(primary, never()).snapshotExists(any());
    }
}
