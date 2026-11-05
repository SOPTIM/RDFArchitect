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
import org.rdfarchitect.services.update.graph.ImportOutcome.GraphsImported;
import org.rdfarchitect.services.update.graph.ImportOutcome.PackagesScanned;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class ImportJobTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Instant STARTED_AT = Instant.parse("2026-09-22T09:00:00Z");

    private static final PlannedImport PLANNED_SECOND = new PlannedImport(1, "second.ttl", 10);

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
    void awaitResolvedPrefixes_handedADecision_returnsItAndGoesOn() throws Exception {
        var job = job();
        var answer = new AtomicReference<ResolvedPrefixes>();
        var waiter = waitForResolutions(job, answer);

        awaitWaiting(job);
        var decisions =
                ResolvedPrefixes.of(
                        List.of(COMPARISON),
                        List.of(
                                new PrefixResolutionDTO(
                                        "cim:",
                                        "http://iec.ch/TC57/2023/CIM-schema-cim18#",
                                        PrefixResolutionDTO.Action.RENAME,
                                        "cim2:")));
        assertThat(job.applyResolvedPrefixes(decisions)).isTrue();
        waiter.join();

        assertThat(answer.get()).isSameAs(decisions);
        assertThat(job.status().state()).isEqualTo(JobState.RUNNING);
        assertThat(job.status().prefixComparison()).isEmpty();
    }

    @Test
    void awaitResolvedPrefixes_cancelled_stopsWaitingWithoutADecision() throws Exception {
        var job = job();
        var answer = new AtomicReference<ResolvedPrefixes>();
        var waiter = waitForResolutions(job, answer);

        awaitWaiting(job);
        job.requestCancel();
        waiter.join();

        assertThat(answer.get()).isSameAs(ResolvedPrefixes.none());
    }

    @Test
    void awaitResolvedPrefixes_withoutConflicts_doesNotWaitAtAll() {
        var job = job();

        assertThat(job.awaitResolvedPrefixes(List.of())).isSameAs(ResolvedPrefixes.none());
        assertThat(job.status().state()).isEqualTo(JobState.RUNNING);
    }

    @Test
    void applyResolvedPrefixes_whenTheJobIsNotWaiting_isRefused() {
        assertThat(job().applyResolvedPrefixes(ResolvedPrefixes.none())).isFalse();
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

    @Test
    void fail_graphImport_recoversWhatWasWrittenBeforeItDied() {
        var job = job();
        job.planned(List.of(new PlannedImport(0, "first.ttl", 10), PLANNED_SECOND));
        job.started(0);
        job.finished(0, Outcome.IMPORTED, "http://graph#first");
        job.started(1);

        job.fail("the store went away");

        var written = outcomeOf(job, GraphsImported.class);
        assertThat(written.graphUris()).containsExactly("http://graph#first");
        assertThat(written.failedFileNames()).containsExactly("second.ttl");
    }

    @Test
    void fail_exportScan_recoversNothing_havingStoredNothing() {
        var job = scanJob();
        job.planned(List.of(new PlannedImport(0, "model.xmi", 10)));
        job.started(0);

        job.fail("the export broke off");

        assertThat(outcomeOf(job, PackagesScanned.class).packages()).isEmpty();
    }

    @Test
    void status_whileRunning_hasNoOutcomeYet() {
        assertThat(job().status().outcome()).isNull();
    }

    private static <T extends ImportOutcome> T outcomeOf(ImportJob job, Class<T> kind) {
        var outcome = job.status().outcome();
        assertThat(outcome).isInstanceOf(kind);
        return kind.cast(outcome);
    }

    // -------------------------------------------------------------------------
    // Being given up on
    // -------------------------------------------------------------------------

    @Test
    void isForgotten_whileTheJobIsStillRunning_isFalseHoweverLongAgoItWasPolled() {
        // Dropping a running job would leave it writing into a store nobody can ask about any
        // more, and would free a session slot the job still holds.
        var job = job();

        assertThat(job.isForgotten(STARTED_AT.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void isForgotten_whenTheJobFinishedButIsStillBeingPolled_isFalse() {
        // An open dialog says so with every poll, and a read keeps an export of a hundred
        // megabytes for exactly as long as someone is still choosing from it.
        var job = job();
        job.complete(GraphsImported.nothing());
        job.markPolled(STARTED_AT.plus(Duration.ofHours(2)));

        assertThat(job.isForgotten(STARTED_AT.plus(Duration.ofHours(1)))).isFalse();
    }

    @Test
    void isForgotten_whenNobodyHasLookedSinceTheThreshold_isTrue() {
        var job = job();
        job.complete(GraphsImported.nothing());
        job.markPolled(STARTED_AT);

        assertThat(job.isForgotten(STARTED_AT.plus(Duration.ofMinutes(1)))).isTrue();
    }

    private ImportJob job() {
        return new ImportJob(
                UUID.randomUUID(), "session-a", "ds", ImportKind.GRAPH_IMPORT, STARTED_AT);
    }

    private ImportJob scanJob() {
        return new ImportJob(
                UUID.randomUUID(), "session-a", "ds", ImportKind.EXPORT_SCAN, STARTED_AT);
    }

    private Thread waitForResolutions(ImportJob job, AtomicReference<ResolvedPrefixes> answer) {
        return Thread.ofVirtual()
                .start(() -> answer.set(job.awaitResolvedPrefixes(List.of(COMPARISON))));
    }

    private void awaitWaiting(ImportJob job) {
        await().pollInSameThread().atMost(TIMEOUT).until(job::isAwaitingPrefixResolution);
    }
}
