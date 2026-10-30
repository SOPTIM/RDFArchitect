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

package org.rdfarchitect.xmi.map;

import lombok.Getter;

/**
 * A profile an export does not hold enough of to build.
 *
 * <p>Thrown before the first triple is written rather than part of the way through. A partial
 * export is valid input — it is what a vendor sends when they export a subset — and the failure it
 * causes is not a defect to be reported as a crash but an answer: this file cannot become that
 * profile, and here is what is missing from it.
 *
 * <p>The reason is shown to whoever started the import, so it names what the file lacks rather than
 * what the code expected.
 */
@Getter
public class XmiImportRefusal extends RuntimeException {

    /** Which profile could not be built, so that a reason can be shown against its name. */
    private final String profileName;

    public XmiImportRefusal(String profileName, String reason) {
        super(reason);
        this.profileName = profileName;
    }
}
