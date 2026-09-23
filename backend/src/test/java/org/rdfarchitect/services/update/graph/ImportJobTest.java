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

package org.rdfarchitect.services.update.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.JobState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class ImportJobTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Instant STARTED_AT = Instant.parse("2026-09-22T09:00:00Z");

    private static final PrefixComparison COMPARISON =
            new PrefixComparison(
                    "cim:",
                    new PrefixBinding("http://iec.ch/TC57/2013/CIM-schema-cim16#", List.of()),
                    List.of(
                            new PrefixBinding(
                                    "http://iec.ch/TC57/2023/CIM-schema-cim18#",
                                    List.of("dl30.ttl"))),
                    true);

    @Test
    void awaitPrefixResolutions_handedADecision_returnsItAndGoesOn() throws Exception {
        var job = job();
        var answer = new AtomicReference<PrefixResolutions>();
        var waiter = waitForResolutions(job, answer);

        awaitWaiting(job);
        var decisions =
                PrefixResolutions.of(
                        List.of(COMPARISON),
                        List.of(
                                new PrefixResolution(
                                        "cim:",
                                        "http://iec.ch/TC57/2023/CIM-schema-cim18#",
                                        PrefixResolution.Action.RENAME,
                                        "cim2:")));
        assertThat(job.applyPrefixResolutions(decisions)).isTrue();
        waiter.join();

        assertThat(answer.get()).isSameAs(decisions);
        assertThat(job.status().state()).isEqualTo(JobState.RUNNING);
        assertThat(job.status().prefixComparison()).isEmpty();
    }

    @Test
    void awaitPrefixResolutions_cancelled_stopsWaitingWithoutADecision() throws Exception {
        var job = job();
        var answer = new AtomicReference<PrefixResolutions>();
        var waiter = waitForResolutions(job, answer);

        awaitWaiting(job);
        job.requestCancel();
        waiter.join();

        assertThat(answer.get()).isSameAs(PrefixResolutions.none());
    }

    @Test
    void awaitPrefixResolutions_withoutConflicts_doesNotWaitAtAll() {
        var job = job();

        assertThat(job.awaitPrefixResolutions(List.of())).isSameAs(PrefixResolutions.none());
        assertThat(job.status().state()).isEqualTo(JobState.RUNNING);
    }

    @Test
    void applyPrefixResolutions_whenTheJobIsNotWaiting_isRefused() {
        assertThat(job().applyPrefixResolutions(PrefixResolutions.none())).isFalse();
    }

    @Test
    void isAbandoned_waitingAndNoLongerPolled_isTrue() throws Exception {
        var job = job();
        var waiter = waitForResolutions(job, new AtomicReference<>());
        awaitWaiting(job);

        assertThat(job.isAbandoned(STARTED_AT.plusSeconds(60))).isTrue();

        job.requestCancel();
        waiter.join();
    }

    @Test
    void isAbandoned_stillBeingPolled_isFalse() throws Exception {
        var job = job();
        var waiter = waitForResolutions(job, new AtomicReference<>());
        awaitWaiting(job);

        job.markPolled(STARTED_AT.plusSeconds(90));

        assertThat(job.isAbandoned(STARTED_AT.plusSeconds(60))).isFalse();

        job.requestCancel();
        waiter.join();
    }

    @Test
    void isAbandoned_jobThatIsNotWaiting_isFalse() {
        assertThat(job().isAbandoned(STARTED_AT.plusSeconds(600))).isFalse();
    }

    private ImportJob job() {
        return new ImportJob(UUID.randomUUID(), "session-a", "ds", STARTED_AT);
    }

    private Thread waitForResolutions(ImportJob job, AtomicReference<PrefixResolutions> answer) {
        return Thread.ofVirtual()
                .start(() -> answer.set(job.awaitPrefixResolutions(List.of(COMPARISON))));
    }

    private void awaitWaiting(ImportJob job) {
        await().pollInSameThread().atMost(TIMEOUT).until(job::isAwaitingPrefixResolution);
    }
}
