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

package org.rdfarchitect.rdf.graph.wrapper;

import org.rdfarchitect.rdf.graph.DeltaCompressible;

/**
 * A participant that records what each of its versions changed, so that a changelog entry can show
 * it. Stepping through the versions is {@link
 * org.rdfarchitect.models.changelog.ChangeLogParticipant}.
 */
public interface DeltaSource {

    /**
     * Returns the delta of the most recent version.
     *
     * @return the additions and deletions that produced the current state
     */
    DeltaCompressible getLastDelta();
}
