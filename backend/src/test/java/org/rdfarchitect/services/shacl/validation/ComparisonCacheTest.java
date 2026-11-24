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

package org.rdfarchitect.services.shacl.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** How long the copies a buffer is compared with are kept, and for whom. */
class ComparisonCacheTest {

    private final AtomicLong now = new AtomicLong();
    private final ComparisonCache cache = new ComparisonCache(Duration.ofMinutes(30), 2, now::get);

    private static final UUID VERSION = UUID.randomUUID();

    private static ComparisonCache.Key key(String session) {
        return new ComparisonCache.Key(session, "cgmes");
    }

    @Test
    void keepsEachSessionsCopyWhileItsDocumentsStayAsTheyAre() {
        cache.put(key("a"), VERSION, List.of());
        cache.put(key("b"), VERSION, List.of());

        assertThat(cache.get(key("a"), VERSION)).isEmpty();
        assertThat(cache.get(key("b"), VERSION)).isEmpty();
        assertThat(cache.get(key("a"), UUID.randomUUID())).isNull();
    }

    @Test
    void releasesACopyNobodyAskedForInTheIdleTime() {
        cache.put(key("a"), VERSION, List.of());
        cache.put(key("b"), VERSION, List.of());
        now.addAndGet(Duration.ofMinutes(20).toMillis());
        cache.get(key("b"), VERSION);
        now.addAndGet(Duration.ofMinutes(20).toMillis());

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(key("a"), VERSION)).isNull();
        assertThat(cache.get(key("b"), VERSION)).isNotNull();
    }

    @Test
    void keepsNoMoreThanItsBoundDroppingTheLeastRecentlyUsed() {
        cache.put(key("a"), VERSION, List.of());
        cache.put(key("b"), VERSION, List.of());
        cache.get(key("a"), VERSION);
        cache.put(key("c"), VERSION, List.of());

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get(key("b"), VERSION)).isNull();
        assertThat(cache.get(key("a"), VERSION)).isNotNull();
    }
}
