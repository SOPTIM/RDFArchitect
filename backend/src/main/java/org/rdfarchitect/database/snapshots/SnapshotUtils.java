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

package org.rdfarchitect.database.snapshots;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@UtilityClass
public class SnapshotUtils {

    public static final String SNAPSHOT_PREFIX = "SNAPSHOT_";

    public static String generateBase64Token() {
        var seed = (new SecureRandom()).generateSeed(16);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(seed);
    }

    public static String constructSnapshotName(String datasetName, String base64Token) {
        return SNAPSHOT_PREFIX + datasetName + "_" + base64Token;
    }

    public static String findSnapshotName(List<String> datasetNames, String base64Token) {
        return datasetNames.stream()
                .filter(name -> name.endsWith(base64Token))
                .findFirst()
                .orElse(null);
    }
}
