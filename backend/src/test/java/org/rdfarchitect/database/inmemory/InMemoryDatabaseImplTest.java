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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.exception.database.ResourceConflictException;

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Test
    void createDataset_existingWorkspace_isRefused() {
        database.createDataset(WORKSPACE);

        assertThatThrownBy(() -> database.createDataset(WORKSPACE))
                .isInstanceOf(ResourceConflictException.class);
    }

    /**
     * Whoever creates a workspace may delete it again when it goes wrong, so only one of several
     * concurrent calls for the same name may be told it created the workspace.
     */
    @Test
    void createDataset_calledConcurrentlyForOneName_createsItForOneCallerOnly() throws Exception {
        var callers = 8;
        var barrier = new CyclicBarrier(callers);
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (var round = 0; round < 200; round++) {
                var name = WORKSPACE + round;
                var created = new ArrayList<Future<Boolean>>();
                for (var caller = 0; caller < callers; caller++) {
                    created.add(executor.submit(() -> createInSession(name, barrier)));
                }
                var creators = 0;
                for (var result : created) {
                    creators += result.get() ? 1 : 0;
                }
                assertThat(creators).as("callers told they created %s", name).isEqualTo(1);
            }
        }
    }

    private boolean createInSession(String name, CyclicBarrier barrier) throws Exception {
        SessionContext.setSessionId("session");
        try {
            barrier.await();
            database.createDataset(name);
            return true;
        } catch (ResourceConflictException e) {
            return false;
        } finally {
            SessionContext.clear();
        }
    }
}
