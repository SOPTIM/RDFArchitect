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

package org.rdfarchitect.rdf.graph;

import org.apache.jena.shared.JenaException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A {@link PrefixMapping} implementation that records prefix changes as deltas similar to the
 * {@link org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta}
 */
public class DeltaPrefixMapping implements PrefixMapping {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("([A-Za-z][A-Za-z0-9-_.]*)?");

    private final PrefixMapping base;
    private final Map<String, String> additions = new HashMap<>();
    private final Map<String, String> deletions = new HashMap<>();
    private boolean locked = false;

    public DeltaPrefixMapping(PrefixMapping base) {
        this.base = base;
    }

    // -------------------------------------------------------------------------
    // Delta access — used by DeltaCompressible to detect and fold changes
    // -------------------------------------------------------------------------

    /**
     * @return an unmodifiable view of the prefix additions relative to the base
     */
    public Map<String, String> getAddedPrefixes() {
        return Map.copyOf(additions);
    }

    /**
     * @return an unmodifiable view of the prefix names removed relative to the base
     */
    public Map<String, String> getDeletedPrefixes() {
        return Map.copyOf(deletions);
    }

    /**
     * @return {@code true} if any prefix has been added or removed relative to the base
     */
    public boolean hasChanges() {
        return !additions.isEmpty() || !deletions.isEmpty();
    }

    /**
     * Builds a plain {@link PrefixMapping} snapshot of the current folded state (base − deletions +
     * additions). Read-only queries delegate to this so resolution behaves exactly like Jena.
     */
    private PrefixMapping folded() {
        return new PrefixMappingImpl().setNsPrefixes(getNsPrefixMap());
    }

    private void checkUnlocked() {
        if (locked) {
            throw new JenaException("Attempted to modify a locked PrefixMapping.");
        }
    }

    private void validatePrefix(String prefix, String uri) {
        Objects.requireNonNull(uri, "null URIs are prohibited as arguments to setNsPrefix");
        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new PrefixMapping.IllegalPrefixException(prefix);
        }
    }

    // -------------------------------------------------------------------------
    // Writes — record the change as a delta
    // -------------------------------------------------------------------------

    @Override
    public PrefixMapping setNsPrefix(String prefix, String uri) {
        checkUnlocked();
        validatePrefix(prefix, uri);
        // Mirror DeltaCompressible#performAdd: setting a prefix to its base value is not a change.
        if (uri.equals(base.getNsPrefixURI(prefix))) {
            additions.remove(prefix);
            deletions.remove(prefix);
            return this;
        }
        additions.put(prefix, uri);
        deletions.remove(prefix);
        return this;
    }

    @Override
    public PrefixMapping removeNsPrefix(String prefix) {
        checkUnlocked();
        additions.remove(prefix);
        String baseUri = base.getNsPrefixURI(prefix);
        if (baseUri != null) {
            deletions.put(prefix, baseUri);
        }
        return this;
    }

    @Override
    public PrefixMapping clearNsPrefixMap() {
        checkUnlocked();
        additions.clear();
        deletions.clear();
        deletions.putAll(base.getNsPrefixMap());
        return this;
    }

    @Override
    public PrefixMapping setNsPrefixes(PrefixMapping other) {
        return setNsPrefixes(other.getNsPrefixMap());
    }

    @Override
    public PrefixMapping setNsPrefixes(Map<String, String> other) {
        checkUnlocked();
        other.forEach(this::setNsPrefix);
        return this;
    }

    @Override
    public PrefixMapping withDefaultMappings(PrefixMapping other) {
        checkUnlocked();
        other.getNsPrefixMap()
                .forEach(
                        (prefix, uri) -> {
                            if (getNsPrefixURI(prefix) == null && getNsURIPrefix(uri) == null) {
                                setNsPrefix(prefix, uri);
                            }
                        });
        return this;
    }

    // -------------------------------------------------------------------------
    // Reads — fold the delta over the base: base − deletions + additions
    // -------------------------------------------------------------------------

    @Override
    public Map<String, String> getNsPrefixMap() {
        Map<String, String> folded = new HashMap<>(base.getNsPrefixMap());
        deletions.keySet().forEach(folded::remove);
        folded.putAll(additions);
        return folded;
    }

    @Override
    public String getNsPrefixURI(String prefix) {
        if (additions.containsKey(prefix)) {
            return additions.get(prefix);
        }
        if (deletions.containsKey(prefix)) {
            return null;
        }
        return base.getNsPrefixURI(prefix);
    }

    @Override
    public String getNsURIPrefix(String uri) {
        return folded().getNsURIPrefix(uri);
    }

    @Override
    public String expandPrefix(String prefixed) {
        return folded().expandPrefix(prefixed);
    }

    @Override
    public String shortForm(String uri) {
        return folded().shortForm(uri);
    }

    @Override
    public String qnameFor(String uri) {
        return folded().qnameFor(uri);
    }

    @Override
    public int numPrefixes() {
        return getNsPrefixMap().size();
    }

    @Override
    public boolean samePrefixMappingAs(PrefixMapping other) {
        return getNsPrefixMap().equals(other.getNsPrefixMap());
    }

    @Override
    public PrefixMapping lock() {
        this.locked = true;
        return this;
    }
}
