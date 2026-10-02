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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Makes sure a request hands its thread back without a transaction on it.
 *
 * <p>A workspace transaction lives in a thread-local and gives the lock back when it is closed. A
 * request that fails to close one therefore poisons the thread it ran on: the workspace stays
 * locked, and every later request served by that thread from the pool counts as being inside that
 * workspace, so a transaction on any other workspace throws. Nothing in the application opens a
 * transaction without try-with-resources; this is the net under that, and reaching it means there
 * is a bug to fix, hence the error-level log.
 *
 * <p>Outermost in the chain, so that it runs after every other filter has had its turn.
 */
public class StaleTransactionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(StaleTransactionFilter.class);

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            var workspaceName = WorkspaceTransactionContext.abandonCurrentTransaction();
            if (workspaceName != null) {
                logger.error(
                        "Request ended with an open transaction on workspace '{}'. It was rolled "
                                + "back and the lock released; the transaction was not closed by "
                                + "its caller.",
                        workspaceName);
            }
        }
    }
}
