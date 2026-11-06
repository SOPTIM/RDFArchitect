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

package org.rdfarchitect.models.cim.data.dto.facade.header;

import java.util.Set;

/**
 * The header of a schema, read lazily from the graph behind an {@link
 * org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade}. It is the block that says which
 * profile the schema is and which version of it: an {@code owl:Ontology} entry in CGMES 3.0, a
 * {@code Package_FileHeaderProfile} category or an ENTSO-E {@code …Version} class in CGMES 2.4.15.
 *
 * <p>A schema without a recognisable header is not an error, so implementations never throw for a
 * missing header or a missing field. Callers check {@link #isPresent()} or handle the empty values
 * the getters return.
 */
public interface ICIMProfileHeader {

    /**
     * Whether the graph holds a header at all.
     *
     * @return false when no header was found, in which case every other getter returns nothing
     */
    boolean isPresent();

    /**
     * The short name the profile goes by, {@code dcat:keyword} in CGMES 3.0, for instance {@code
     * EQ}, {@code SSH} or {@code FH} for a file header profile.
     *
     * @return the keyword, or null when the header does not carry one
     */
    String getKeyword();

    /**
     * The version IRIs of the profile, {@code owl:versionIRI} in CGMES 3.0 and the fixed {@code
     * entsoeURI} / {@code baseURI} values in CGMES 2.4.15, where a profile can carry several of
     * them. Surrounding whitespace is trimmed.
     *
     * @return the version IRIs, empty when the header carries none
     */
    Set<String> getVersionIris();

    /**
     * The version of the profile as plain text, {@code owl:versionInfo}, for instance {@code
     * 3.0.0}.
     *
     * @return the version info, or null when the header does not carry one
     */
    String getVersionInfo();
}
