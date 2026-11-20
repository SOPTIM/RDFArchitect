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

package org.rdfarchitect.api.dto.desktop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A workspace file the operating system asked the desktop app to open. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenRequestDTO {

    /** What a window names the request by, {@code /mainpage?open=<id>}. */
    private String id;

    /** The name of the file, without its directory. */
    private String fileName;

    /** Where the file is; only given to whoever registers it. */
    private String path;
}
