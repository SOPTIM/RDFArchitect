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

package org.rdfarchitect.services.archive.parts;

import com.fasterxml.jackson.core.type.TypeReference;

import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.util.LinkedHashMap;
import java.util.Map;

/** The colour each schema is drawn in on the cross-profile diagram, {@code colors.json}. */
public final class ColorsPart
        implements WorkspaceFilePart<WorkspaceTransaction, Map<String, String>> {

    @Override
    public void write(WorkspaceTransaction source, PartWriter out) {
        var colors = new LinkedHashMap<String, String>();
        for (var schemaName : out.schemaNames()) {
            var color = source.crossProfileColors().getColor(schemaName);
            if (color != null) {
                colors.put(schemaName, color);
            }
        }
        out.writeJson(WorkspaceArchiveFormat.COLORS, colors);
    }

    /**
     * @return the colours by schema name
     */
    @Override
    public Map<String, String> read(PartReader in) {
        var archived =
                in.readJson(
                        WorkspaceArchiveFormat.COLORS, new TypeReference<Map<String, String>>() {});
        if (archived == null) {
            return null;
        }
        var valid = true;
        for (var entry : archived.entrySet()) {
            var schemaName = entry.getKey();
            if (!in.schemaNames().contains(schemaName)) {
                in.problem(
                        WorkspaceArchiveFormat.COLORS,
                        "\"" + schemaName + "\" is not a schema of the workspace file.");
                valid = false;
            } else if (entry.getValue().isBlank()) {
                in.problem(
                        WorkspaceArchiveFormat.COLORS,
                        "the schema \"" + schemaName + "\" has no colour.");
                valid = false;
            }
        }
        return valid ? archived : null;
    }

    @Override
    public void apply(WorkspaceTransaction target, Map<String, String> colors) {
        colors.forEach(target.crossProfileColors()::setColor);
    }
}
