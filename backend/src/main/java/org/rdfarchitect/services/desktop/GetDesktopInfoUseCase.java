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

import org.rdfarchitect.api.dto.desktop.DesktopInfoDTO;

/** Identifies the running desktop app. */
public interface GetDesktopInfoUseCase {

    /**
     * Returns what identifies the running desktop app, so that a second start can tell whether to
     * hand over to it.
     *
     * @return the application name and its version
     */
    DesktopInfoDTO getDesktopInfo();
}
