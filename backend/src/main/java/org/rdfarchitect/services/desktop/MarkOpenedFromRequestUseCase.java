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

package org.rdfarchitect.services.desktop;

/** Records that a workspace was opened from a file the operating system handed over. */
public interface MarkOpenedFromRequestUseCase {

    /** What became of the request. */
    enum Result {
        /** The workspace counts as saved, and Save writes to the file. */
        RECORDED,
        /** There is no open request of that id. */
        UNKNOWN_REQUEST,
        /** The workspace is being written to right now; the request is kept to try again. */
        BUSY
    }

    /**
     * Records that the workspace holds the content of the open request's file, so that it counts as
     * saved and Save writes to that file. The request is removed once this is recorded.
     *
     * @param workspaceName the workspace, in the current session
     * @param requestId the open request, see {@link RequestOpenFileUseCase}
     * @return what became of the request
     * @throws org.rdfarchitect.exception.database.ResourceNotFoundException if there is no such
     *     workspace
     */
    Result markOpenedFromRequest(String workspaceName, String requestId);
}
