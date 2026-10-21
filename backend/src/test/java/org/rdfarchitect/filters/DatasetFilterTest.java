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

package org.rdfarchitect.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.security.DatasetAccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Covers which requests reach a controller before the dataset they address exists. The import is
 * the case that matters: it brings its dataset into being, and reading an export does not create
 * one at all, so a filter that insisted on the dataset would turn both into an access denial.
 */
class DatasetFilterTest {

    private DatasetFilter filter;

    @BeforeEach
    void setUp() {
        var databasePort = mock(DatabasePort.class);
        when(databasePort.listDatasets()).thenReturn(List.of("existing"));
        filter = new DatasetFilter(databasePort);
    }

    @Test
    void startingAnImportIntoADatasetThatDoesNotExistIsLetThrough() throws Exception {
        assertThat(reaches("POST", "/api/datasets/brandnew/imports")).isTrue();
    }

    @Test
    void readingAnExportForADatasetThatDoesNotExistIsLetThrough() throws Exception {
        assertThat(reaches("POST", "/api/datasets/brandnew/imports/xmi")).isTrue();
    }

    @Test
    void followingAnImportOfADatasetThatNeverCameIntoExistenceIsLetThrough() throws Exception {
        var jobId = "/api/datasets/brandnew/imports/3f1b6a5e-0000-0000-0000-000000000000";

        assertThat(reaches("GET", jobId)).isTrue();
        assertThat(reaches("DELETE", jobId)).isTrue();
        assertThat(reaches("PUT", jobId + "/prefix-resolutions")).isTrue();
    }

    @Test
    void anotherRequestForADatasetThatDoesNotExistIsRefused() {
        assertThatThrownBy(() -> reaches("GET", "/api/datasets/brandnew/graphs"))
                .isInstanceOf(DatasetAccessDeniedException.class);
    }

    @Test
    void aRequestForADatasetThatExistsIsLetThrough() throws Exception {
        assertThat(reaches("GET", "/api/datasets/existing/graphs")).isTrue();
    }

    /** Whether the request got past the filter to the chain behind it. */
    private boolean reaches(String method, String uri) throws IOException, ServletException {
        var request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        var reached = new AtomicBoolean(false);
        FilterChain recording = (_, _) -> reached.set(true);
        filter.doFilter(request, new MockHttpServletResponse(), recording);
        return reached.get();
    }
}
