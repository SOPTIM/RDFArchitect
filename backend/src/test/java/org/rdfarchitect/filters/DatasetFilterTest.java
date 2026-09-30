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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.security.DatasetAccessDeniedException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

/**
 * Guards the one place where a workspace name is read from the URL by hand.
 *
 * <p>The filter matches path literals and cuts the name out at a fixed offset, so nothing here is
 * checked by the compiler: a wrong prefix or a wrong offset does not fail to build, it silently
 * stops rejecting foreign workspaces or starts rejecting every one.
 */
class DatasetFilterTest {

    private static final String OWN = "myWorkspace";
    private static final String FOREIGN = "someoneElsesWorkspace";

    private DatabasePort databasePort;
    private DatasetFilter filter;
    private MockFilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        when(databasePort.listDatasets()).thenReturn(List.of(OWN));
        filter = new DatasetFilter(databasePort);
        chain = new MockFilterChain();
        response = new MockHttpServletResponse();
    }

    // -------------------------------------------------------------------------
    // The access check itself
    // -------------------------------------------------------------------------

    @Test
    void doFilter_workspaceOfThisSession_passesThrough() throws Exception {
        filter.doFilter(request("GET", "/api/datasets/" + OWN + "/graphs"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_workspaceOfAnotherSession_isRejected() {
        var request = request("GET", "/api/datasets/" + FOREIGN + "/graphs");

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(DatasetAccessDeniedException.class)
                .hasMessageContaining(FOREIGN);
    }

    @Test
    void doFilter_urlEncodedWorkspaceName_isDecodedBeforeTheCheck() throws Exception {
        when(databasePort.listDatasets()).thenReturn(List.of("my workspace"));

        filter.doFilter(request("GET", "/api/datasets/my%20workspace/graphs"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_workspaceNameWithoutTrailingPath_isStillExtracted() {
        var request = request("GET", "/api/datasets/" + FOREIGN);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(DatasetAccessDeniedException.class)
                .hasMessageContaining(FOREIGN);
    }

    @Test
    void doFilter_pathOutsideTheWorkspaceApi_isNotChecked() throws Exception {
        filter.doFilter(request("GET", "/api/session"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(databasePort, never()).listDatasets();
    }

    @Test
    void doFilter_preflight_isNotChecked() throws Exception {
        // Preflight arrives under a different session id, so checking it would reject every
        // cross-origin request.
        filter.doFilter(request("OPTIONS", "/api/datasets/" + FOREIGN), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(databasePort, never()).listDatasets();
    }

    // -------------------------------------------------------------------------
    // Requests allowed to name a workspace that does not exist yet
    // -------------------------------------------------------------------------

    @Test
    void doFilter_putCreatingAWorkspace_isAllowedForAnUnknownName() throws Exception {
        filter.doFilter(request("PUT", "/api/datasets/brandNew"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_uploadBringingItsWorkspace_isAllowedForAnUnknownName() throws Exception {
        filter.doFilter(
                request("PUT", "/api/datasets/brandNew/graphs/http%3A%2F%2Fex.org%2Fg/content"),
                response,
                chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_importProgress_isAllowedForAnUnknownName() throws Exception {
        filter.doFilter(
                request("GET", "/api/datasets/brandNew/graphs/content/imports/42"),
                response,
                chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_getOnAWorkspaceThatDoesNotExist_isRejected() {
        // Only PUT is whitelisted; a GET must not be able to address an unknown workspace.
        var request = request("GET", "/api/datasets/brandNew");

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(DatasetAccessDeniedException.class);
    }

    @Test
    void doFilter_putOnAGraphOfAForeignWorkspace_isRejected() {
        // The whitelist covers the content upload, not every PUT below a workspace.
        var request = request("PUT", "/api/datasets/" + FOREIGN + "/graphs/g/keyword");

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(DatasetAccessDeniedException.class)
                .hasMessageContaining(FOREIGN);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        var request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
