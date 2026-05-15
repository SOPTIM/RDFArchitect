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

package org.rdfarchitect.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSettingsContext {

    private static final ThreadLocal<UserSettings> settings = new ThreadLocal<>();

    public static void set(UserSettings userSettings) {
        settings.set(userSettings);
    }

    public static UserSettings get() {
        var s = settings.get();
        return s != null ? s : UserSettings.defaults();
    }

    public static void clear() {
        settings.remove();
    }

    public record UserSettings(boolean usePackagePrefix, boolean normalizeComments) {
        public static UserSettings defaults() {
            return new UserSettings(true, true);
        }
    }
}
