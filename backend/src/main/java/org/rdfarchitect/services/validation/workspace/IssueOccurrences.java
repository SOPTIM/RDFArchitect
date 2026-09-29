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

package org.rdfarchitect.services.validation.workspace;

import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns what a rule found into the occurrence a report carries: which schema, which resource, and
 * the value that schema holds, rendered as text.
 */
public class IssueOccurrences {

    private final WorkspaceValidationContext context;

    IssueOccurrences(WorkspaceValidationContext context) {
        this.context = context;
    }

    /**
     * Describes a finding in one schema, with the class it belongs to so that the UI can open it.
     *
     * @param value what the schema says, null when the finding has no value to show
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO of(Occurrence<?> occurrence, Object value) {
        var ownerClass = occurrence.ownerClass();
        return builder(occurrence.profile(), occurrence.resource().getUuid(), value)
                .classUUID(ownerClass.getUuid())
                .packageUUID(packageUuidOf(ownerClass))
                .build();
    }

    /**
     * Describes a finding that is about a schema as a whole, such as a duplicate keyword.
     *
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO of(CIMProfileModel profile, Object value) {
        return of(profile, null, value);
    }

    /**
     * Describes a finding about one resource of a schema, without the class around it.
     *
     * @param uuid the resource in that schema, or null when there is none
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO of(CIMProfileModel profile, UUID uuid, Object value) {
        return builder(profile, uuid, value).build();
    }

    private IssueOccurrenceDTO.IssueOccurrenceDTOBuilder builder(
            CIMProfileModel profile, UUID uuid, Object value) {
        return IssueOccurrenceDTO.builder()
                .graphUri(profile.graphUri())
                .keyword(context.getKeyword(profile))
                .uuid(uuid)
                .value(format(value));
    }

    private static UUID packageUuidOf(ICIMClass cimClass) {
        try {
            var category = cimClass.getBelongsToCategory();
            return category == null ? null : category.getUuid();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static String format(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty()
                    ? null
                    : values.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }
}
