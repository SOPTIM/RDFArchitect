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

package org.rdfarchitect.database.inmemory;

import static org.mockito.Mockito.*;

import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryDatabaseAdapterTest {

    private InMemoryDatabase database;
    private InMemoryDatabaseAdapter adapter;

    @BeforeEach
    void setUp() {
        database = mock(InMemoryDatabase.class);
        adapter = new InMemoryDatabaseAdapter(database);
    }

    @Test
    void createWorkspaceIfAbsent_delegatesToDatabase() {
        adapter.createWorkspaceIfAbsent("a-workspace");

        verify(database).createWorkspaceIfAbsent("a-workspace");
    }

    @Test
    void beginTransaction_delegatesToDatabase() {
        adapter.beginTransaction("a-workspace", ReadWrite.READ);

        verify(database).beginTransaction("a-workspace", ReadWrite.READ);
    }
}
