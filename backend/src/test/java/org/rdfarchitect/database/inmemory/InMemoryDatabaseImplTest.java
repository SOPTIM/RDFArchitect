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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;

class InMemoryDatabaseImplTest {

    private static final String WORKSPACE = "ws";

    private InMemoryDatabase database;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId("session");
        database = new InMemoryDatabaseImpl(new SchemaConfig());
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void createWorkspaceIfAbsent_newWorkspace_canBeEditedRightAway() {
        database.createWorkspaceIfAbsent(WORKSPACE);

        assertThat(database.listDatasets()).containsExactly(WORKSPACE);
        assertThat(database.isReadOnly(WORKSPACE)).isFalse();
    }

    @Test
    void createWorkspaceIfAbsent_existingWorkspace_keepsItAsItIs() {
        database.createWorkspaceIfAbsent(WORKSPACE);
        database.disableEditing(WORKSPACE);

        database.createWorkspaceIfAbsent(WORKSPACE);

        assertThat(database.listDatasets()).containsExactly(WORKSPACE);
        assertThat(database.isReadOnly(WORKSPACE)).isTrue();
    }
}
