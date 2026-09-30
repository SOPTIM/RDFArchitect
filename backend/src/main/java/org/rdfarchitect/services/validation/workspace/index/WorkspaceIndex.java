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

package org.rdfarchitect.services.validation.workspace.index;

import org.rdfarchitect.models.cim.data.dto.facade.ICIMAssociation;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.services.rendering.CIMProfileModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceIndex {

    private final Map<String, List<Occurrence<ICIMClass>>> classes = new LinkedHashMap<>();

    private final Map<String, List<Occurrence<ICIMAttribute>>> attributes = new LinkedHashMap<>();

    private final Map<String, List<Occurrence<ICIMAssociation>>> associations =
            new LinkedHashMap<>();

    private final Map<String, List<Occurrence<ICIMEnumEntry>>> enumEntries = new LinkedHashMap<>();

    private final List<ReadFailure> readFailures = new ArrayList<>();

    private final String scopeGraphUri;

    private WorkspaceIndex(String scopeGraphUri) {
        this.scopeGraphUri = scopeGraphUri;
    }

    /**
     * Reads every schema once and indexes its classes, attributes, associations and enum entries by
     * URI. What cannot be read ends up in {@link #readFailures()} instead of failing the run.
     *
     * @param scopeGraphUri the schema the run is about, which narrows the shared views to the URIs
     *     that schema takes part in, or null for the whole workspace
     * @return the index over all schemas
     */
    public static WorkspaceIndex of(List<CIMProfileModel> profiles, String scopeGraphUri) {
        var index = new WorkspaceIndex(scopeGraphUri);
        profiles.forEach(index::addProfile);
        return index;
    }

    /**
     * @return every class by URI, with the schemas it appears in
     */
    public Map<String, List<Occurrence<ICIMClass>>> classes() {
        return Collections.unmodifiableMap(classes);
    }

    /**
     * @return the classes that appear in more than one schema
     */
    public Map<String, List<Occurrence<ICIMClass>>> sharedClasses() {
        return shared(classes);
    }

    /**
     * @return every attribute by URI, with the schemas it appears in
     */
    public Map<String, List<Occurrence<ICIMAttribute>>> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * @return the attributes that appear in more than one schema
     */
    public Map<String, List<Occurrence<ICIMAttribute>>> sharedAttributes() {
        return shared(attributes);
    }

    /**
     * @return every association by URI, with the schemas it appears in
     */
    public Map<String, List<Occurrence<ICIMAssociation>>> associations() {
        return Collections.unmodifiableMap(associations);
    }

    /**
     * @return the associations that appear in more than one schema
     */
    public Map<String, List<Occurrence<ICIMAssociation>>> sharedAssociations() {
        return shared(associations);
    }

    /**
     * @return every enum entry by URI, with the schemas it appears in
     */
    public Map<String, List<Occurrence<ICIMEnumEntry>>> enumEntries() {
        return Collections.unmodifiableMap(enumEntries);
    }

    /**
     * @return the enum entries that appear in more than one schema
     */
    public Map<String, List<Occurrence<ICIMEnumEntry>>> sharedEnumEntries() {
        return shared(enumEntries);
    }

    /**
     * @return the resources and schemas that could not be read while indexing
     */
    public List<ReadFailure> readFailures() {
        return Collections.unmodifiableList(readFailures);
    }

    private void addProfile(CIMProfileModel profile) {
        List<ICIMClass> cimClasses;
        try {
            cimClasses = profile.model().getCIMClasses();
        } catch (RuntimeException e) {
            readFailures.add(new ReadFailure(profile, null, e.getMessage()));
            return;
        }
        for (var cimClass : cimClasses) {
            try {
                addClass(profile, cimClass);
            } catch (RuntimeException e) {
                readFailures.add(new ReadFailure(profile, cimClass.getUuid(), e.getMessage()));
            }
        }
    }

    private void addClass(CIMProfileModel profile, ICIMClass cimClass) {
        add(classes, profile, cimClass, cimClass);
        cimClass.getAttributes()
                .forEach(attribute -> add(attributes, profile, attribute, cimClass));
        cimClass.getAssociations()
                .forEach(association -> add(associations, profile, association, cimClass));
        cimClass.getEnumEntries()
                .forEach(enumEntry -> add(enumEntries, profile, enumEntry, cimClass));
    }

    private static <T extends ICIMResource> void add(
            Map<String, List<Occurrence<T>>> index,
            CIMProfileModel profile,
            T resource,
            ICIMClass ownerClass) {
        index.computeIfAbsent(resource.getUri().toString(), _ -> new ArrayList<>())
                .add(new Occurrence<>(profile, resource, ownerClass));
    }

    private <T extends ICIMResource> Map<String, List<Occurrence<T>>> shared(
            Map<String, List<Occurrence<T>>> index) {
        var shared = new LinkedHashMap<String, List<Occurrence<T>>>();
        index.forEach(
                (uri, occurrences) -> {
                    if (isInSeveralSchemas(occurrences) && isInScope(occurrences)) {
                        shared.put(uri, occurrences);
                    }
                });
        return Collections.unmodifiableMap(shared);
    }

    /**
     * A resource can be indexed twice within one schema, an attribute with two domains for
     * instance, which is not what the shared views are about.
     */
    private static boolean isInSeveralSchemas(List<? extends Occurrence<?>> occurrences) {
        return occurrences.stream()
                        .map(occurrence -> occurrence.profile().graphUri())
                        .distinct()
                        .count()
                > 1;
    }

    private boolean isInScope(List<? extends Occurrence<?>> occurrences) {
        return scopeGraphUri == null
                || occurrences.stream()
                        .anyMatch(
                                occurrence ->
                                        scopeGraphUri.equals(occurrence.profile().graphUri()));
    }
}
